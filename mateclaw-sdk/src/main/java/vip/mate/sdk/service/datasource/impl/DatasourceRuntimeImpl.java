package vip.mate.sdk.service.datasource.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.datasource.service.DatasourceService;
import vip.mate.sdk.service.datasource.DatasourceRuntime;

import java.util.List;

/**
 * 数据源运行时实现
 */
@Service
@RequiredArgsConstructor
public class DatasourceRuntimeImpl implements DatasourceRuntime {

    private final DatasourceService datasourceService;

    @Override
    public List<DatasourceEntity> listDatasources() {
        return datasourceService.listAll();
    }

    @Override
    public DatasourceEntity getDatasource(Long id) {
        return datasourceService.getById(id);
    }

    @Override
    public boolean testDatasourceConnection(Long id) {
        return datasourceService.testConnection(id);
    }

    @Override
    public DatasourceEntity createDatasource(DatasourceEntity entity) {
        return datasourceService.create(entity);
    }

    @Override
    public DatasourceEntity updateDatasource(DatasourceEntity entity) {
        return datasourceService.update(entity);
    }

    @Override
    public void deleteDatasource(Long id) {
        datasourceService.delete(id);
    }

    @Override
    public DatasourceEntity toggleDatasource(Long id, boolean enabled) {
        return datasourceService.toggle(id, enabled);
    }
}
