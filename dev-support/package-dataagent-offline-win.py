"""由 Maven package 调用：生成 Windows x64 / Python 3.12 离线部署包。"""

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


def package(jar: Path, worker: Path, output: Path) -> None:
    jar, worker, output = jar.resolve(), worker.resolve(), output.resolve()
    requirements = worker / "requirements-worker.txt"
    for required in (jar, requirements, worker / "src/runner/worker.py"):
        if not required.is_file():
            raise FileNotFoundError("离线打包缺少文件：{}".format(required))
    output.parent.mkdir(parents=True, exist_ok=True)
    # 只清理本次创建的临时目录；不复制开发环境的 .venv 和缓存。
    with tempfile.TemporaryDirectory(prefix="dataagent-offline-win-") as temporary:
        root = Path(temporary) / "DataAgent"
        packaged_worker = root / "python-worker"
        wheelhouse = packaged_worker / "wheelhouse"
        wheelhouse.mkdir(parents=True)
        shutil.copy2(jar, root / jar.name)
        shutil.copytree(worker / "src", packaged_worker / "src",
                        ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
        shutil.copy2(requirements, packaged_worker / "requirements.txt")
        (root / "部署说明.txt").write_text(
            "Windows x64 离线部署（需要 Java 21 和 Python 3.12）\n"
            "在本目录打开 CMD，保留数据库等配置，然后执行：\n"
            "set \"PYTHON_COMMAND=\"\n"
            "set \"MATECLAW_PYTHON_WORKER_HOME=%CD%\\python-worker\"\n"
            "java -jar {}\n".format(jar.name)
            + "首次启动自动创建 .venv 并从 wheelhouse 安装依赖，无需联网或手动 pip。\n"
            "请部署整个目录，不要只复制 JAR；依赖安装失败会打印到后端日志。\n",
            encoding="utf-8-sig",
        )
        # 在构建机下载目标平台的 wheel，不能打包 macOS 的虚拟环境。
        subprocess.run([
            sys.executable, "-m", "pip", "download",
            "--disable-pip-version-check", "--require-hashes",
            "--requirement", str(packaged_worker / "requirements.txt"),
            "--dest", str(wheelhouse), "--only-binary=:all:",
            "--platform", "win_amd64", "--python-version", "3.12",
            "--implementation", "cp", "--abi", "cp312",
        ], check=True)
        if not any(wheelhouse.glob("*.whl")):
            raise RuntimeError("离线打包失败：没有下载到 Windows wheel")
        archive = Path(temporary) / output.name
        with ZipFile(archive, "w", ZIP_DEFLATED) as bundle:
            for path in sorted(root.rglob("*")):
                if path.is_file():
                    bundle.write(path, path.relative_to(root.parent))
        with ZipFile(archive) as bundle:
            invalid = bundle.testzip()
            if invalid:
                raise RuntimeError("部署包完整性检查失败：{}".format(invalid))
        shutil.copy2(archive, output)
    print("Windows Python 3.12 离线部署包已生成：{}".format(output), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--worker", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    options = parser.parse_args()
    package(options.jar, options.worker, options.output)
