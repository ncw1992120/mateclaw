"""
MateClaw DataAgent Python Client SDK 安装脚本
"""

import re

from setuptools import find_packages, setup

# 以 mateclaw_dataagent/__init__.py 的 __version__ 为版本号唯一来源
with open("mateclaw_dataagent/__init__.py", "r", encoding="utf-8") as fh:
    _version_match = re.search(r'__version__ = "([^"]+)"', fh.read())
    if not _version_match:
        raise RuntimeError("无法从 mateclaw_dataagent/__init__.py 解析版本号")
    _version = _version_match.group(1)

with open("README_CN.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()

setup(
    name="mateclaw-dataagent-client",
    version=_version,
    author="MateClaw Team",
    author_email="mateclaw@example.com",
    description="MateClaw DataAgent Python Client SDK for AI Evaluation",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/mateclaw/mateclaw-dataagent-client-python",
    packages=find_packages(),
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "Topic :: Software Development :: Libraries :: Python Modules",
        "License :: OSI Approved :: MIT License",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Programming Language :: Python :: 3.12",
    ],
    python_requires=">=3.10",
    install_requires=[
        "requests>=2.28.0",
    ],
    extras_require={
        "dev": [
            "pytest>=7.0.0",
            "pytest-asyncio>=0.21.0",
            "black>=23.0.0",
            "ruff>=0.1.0",
        ],
    },
)
