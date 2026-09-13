package vip.mate.dataagent.objectref;

import vip.mate.dataagent.dataset.DatasetAccessContext;
import vip.mate.dataagent.dataset.ObjectRef;

import java.io.InputStream;

public interface ObjectRefService {
    ObjectRef put(DatasetAccessContext context, String format, InputStream content);

    InputStream open(DatasetAccessContext context, ObjectRef reference);

    void expire(ObjectRef reference);
}
