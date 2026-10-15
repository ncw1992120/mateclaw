package vip.mate.dataagent.dataset.file;

import vip.mate.dataagent.dataset.DatasetColumn;
import vip.mate.dataagent.dataset.DatasetReadErrorCode;
import vip.mate.dataagent.dataset.DatasetReadException;

import java.util.List;
import java.util.Objects;

/** 已发布文件 Schema 的显式版本校验；字段变化必须升级版本。 */
public final class SchemaVersionValidator {
    private SchemaVersionValidator() {}
    public static void requireCompatible(int publishedVersion, int incomingVersion,
                                         List<DatasetColumn> published, List<DatasetColumn> incoming) {
        if (publishedVersion <= 0 || incomingVersion <= 0) throw invalid("schema version is required");
        if (incomingVersion < publishedVersion) throw invalid("incoming schema version is older than published version");
        if (incomingVersion == publishedVersion && !sameShape(published, incoming))
            throw new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, "文件 Schema 发生漂移，请创建新版本");
    }
    private static boolean sameShape(List<DatasetColumn> a, List<DatasetColumn> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i=0;i<a.size();i++) { DatasetColumn x=a.get(i), y=b.get(i); if (!Objects.equals(x.name(),y.name()) || !Objects.equals(x.dataType(),y.dataType()) || x.nullable()!=y.nullable()) return false; }
        return true;
    }
    private static DatasetReadException invalid(String message) { return new DatasetReadException(DatasetReadErrorCode.INVALID_REQUEST, message); }
}
