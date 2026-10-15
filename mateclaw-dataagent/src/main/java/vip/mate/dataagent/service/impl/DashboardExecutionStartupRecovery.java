package vip.mate.dataagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vip.mate.dataagent.model.DashboardExecutionEntity;
import vip.mate.dataagent.repository.DashboardExecutionMapper;

import java.util.List;

/** Marks executions left behind by a previous DataAgent process as terminal. */
@Component
public class DashboardExecutionStartupRecovery implements ApplicationRunner {
    static final String RESTART_ERROR = "DataAgent restarted before Python execution completed";

    private final DashboardExecutionMapper executionMapper;

    public DashboardExecutionStartupRecovery(DashboardExecutionMapper executionMapper) {
        this.executionMapper = executionMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DashboardExecutionEntity> inFlight = executionMapper.selectList(new LambdaQueryWrapper<DashboardExecutionEntity>()
                .in(DashboardExecutionEntity::getStatus, "RUNNING", "SUBMITTING"));
        for (DashboardExecutionEntity execution : inFlight) {
            execution.setStatus("FAILED");
            execution.setErrorMessage(RESTART_ERROR);
            execution.setReturnCode(1);
            executionMapper.updateById(execution);
        }
    }
}
