package vip.mate.client.api;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import vip.mate.client.constant.ApiPathConstants;
import vip.mate.client.model.Datasource;
import vip.mate.client.model.R;
import vip.mate.client.model.response.DatasourceTestResp;

import java.util.List;

/**
 * 数据源管理客户端
 * <p>
 * 对应服务端 /api/v1/datasources 接口，提供数据源的增删改查、连接测试、启停等功能
 */
public class DatasourceClient extends AbstractApiClient {

    public DatasourceClient(String baseUrl, RestTemplate restTemplate) {
        super(baseUrl, restTemplate);
    }

    /**
     * 获取数据源列表
     *
     * @return 数据源列表
     */
    public R<List<Datasource>> list() {
        return get(ApiPathConstants.DATASOURCE, new ParameterizedTypeReference<R<List<Datasource>>>() {});
    }

    /**
     * 获取数据源详情
     *
     * @param id 数据源 ID
     * @return 数据源详情
     */
    public R<Datasource> get(Long id) {
        return get(resolvePath(ApiPathConstants.DATASOURCE_BY_ID, id), new ParameterizedTypeReference<R<Datasource>>() {});
    }

    /**
     * 创建数据源
     *
     * @param entity 数据源信息
     * @return 创建的数据源信息
     */
    public R<Datasource> create(Datasource entity) {
        return post(ApiPathConstants.DATASOURCE, entity, new ParameterizedTypeReference<R<Datasource>>() {});
    }

    /**
     * 更新数据源
     *
     * @param id     数据源 ID
     * @param entity 数据源更新信息
     * @return 更新后的数据源信息
     */
    public R<Datasource> update(Long id, Datasource entity) {
        return put(resolvePath(ApiPathConstants.DATASOURCE_BY_ID, id), entity, new ParameterizedTypeReference<R<Datasource>>() {});
    }

    /**
     * 删除数据源
     *
     * @param id 数据源 ID
     * @return 操作结果
     */
    public R<Void> delete(Long id) {
        return delete(resolvePath(ApiPathConstants.DATASOURCE_BY_ID, id), new ParameterizedTypeReference<R<Void>>() {});
    }

    /**
     * 测试数据源连接
     *
     * @param id 数据源 ID
     * @return 连接测试结果
     */
    public R<DatasourceTestResp> testConnection(Long id) {
        return post(resolvePath(ApiPathConstants.DATASOURCE_TEST, id), null, new ParameterizedTypeReference<R<DatasourceTestResp>>() {});
    }

    /**
     * 切换数据源启用/禁用状态
     *
     * @param id      数据源 ID
     * @param enabled 是否启用
     * @return 更新后的数据源信息
     */
    public R<Datasource> toggle(Long id, boolean enabled) {
        String path = resolvePath(ApiPathConstants.DATASOURCE_TOGGLE, id) + "?enabled=" + enabled;
        return put(path, new ParameterizedTypeReference<R<Datasource>>() {});
    }
}
