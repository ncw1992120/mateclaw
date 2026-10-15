package vip.mate.dataagent.service;

import vip.mate.dataagent.dto.FinalResultQueryConfigDTO;
import vip.mate.dataagent.dto.FinalResultQueryContextDTO;
import vip.mate.dataagent.service.code.ScriptResultContractService;

/** 对 Python 已返回并通过契约校验的最终结果做内存查询。 */
public interface FinalResultQueryService {
    FinalResultPage query(ScriptResultContractService.ValidatedEnvelope envelope,
                          FinalResultQueryConfigDTO config,
                          FinalResultQueryContextDTO context);

    record FinalResultPage(ScriptResultContractService.ValidatedEnvelope envelope,
                           int totalRows, int page, int pageSize) {}
}
