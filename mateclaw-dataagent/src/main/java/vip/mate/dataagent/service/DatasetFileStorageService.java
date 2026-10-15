package vip.mate.dataagent.service;

import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.file.StoredFileRef;

import java.io.InputStream;

public interface DatasetFileStorageService {
    StoredFileRef put(DatasetAccessContext context, Long ownerId, String fileName, InputStream content, long size);
    InputStream open(DatasetAccessContext context, StoredFileRef reference);
    void delete(DatasetAccessContext context, StoredFileRef reference);
}
