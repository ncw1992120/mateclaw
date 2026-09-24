import importlib.util
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("aloudata-mock-server.py")
MODULE_SPEC = importlib.util.spec_from_file_location("aloudata_mock_server", SCRIPT_PATH)
assert MODULE_SPEC and MODULE_SPEC.loader
aloudata_mock_server = importlib.util.module_from_spec(MODULE_SPEC)
MODULE_SPEC.loader.exec_module(aloudata_mock_server)
aloudata_mock_server.FIXTURES_DIR = str(
    Path(__file__).parents[3] / "mateclaw-dataagent/src/main/resources/mock/aloudata"
)


def test_dimension_values_returns_conversion_metric_names_from_dimension_view():
    assert aloudata_mock_server.ROUTES[
        ("POST", f"{aloudata_mock_server.ANYMETRICS}/dimension/values")
    ] is aloudata_mock_server.handle_dimension_values

    response = aloudata_mock_server.handle_dimension_values(
        {},
        {"dimName": "metric_name", "pageNumber": 1, "pageSize": 200},
        {"auth-value": "mock-user"},
    )

    assert response["success"] is True
    assert response["data"] == [
        "经纪个人客户场内公募非货当年净买入",
        "经纪个人场内公募非货交易量",
        "经纪个人场内公募非货加仓交易量",
    ]


def test_dimension_values_supports_keyword_and_pagination():
    response = aloudata_mock_server.handle_dimension_values(
        {},
        {"dimName": "metric_name", "dimValueKeyword": "加仓", "pageNumber": 1, "pageSize": 1},
        {},
    )

    assert response["data"] == ["经纪个人场内公募非货加仓交易量"]


def test_selector_categories_include_strategy_and_substrategy_nodes():
    response = aloudata_mock_server.handle_category_list(
        {"categoryType": ["CATEGORY_METRIC"]}, {}, {}
    )

    assert response["success"] is True
    assert response["data"] == [
        {"id": "metric-strategy", "name": "策略解读", "parentId": None,
         "categoryType": "CATEGORY_METRIC"},
        {"id": "metric-sub-strategy", "name": "子策略", "parentId": "metric-strategy",
         "categoryType": "CATEGORY_METRIC"},
    ]


def test_metric_list_searches_display_and_field_names_and_filters_category():
    response = aloudata_mock_server.handle_metric_list(
        {"keyword": ["入金"], "metricCategoryId": ["metric-sub-strategy"],
         "pageNumber": ["1"], "pageSize": ["10"]}, {}, {}
    )

    data = response["data"]
    assert data["total"] == 3
    assert {item["metricName"] for item in data["data"]} == {
        "digo_cust_asset_in", "digo_new_cust_asset_in", "digo_cust_asset_in10000"
    }
    assert all(item["metricCategoryId"] == "metric-sub-strategy" for item in data["data"])

    by_field = aloudata_mock_server.handle_metric_list(
        {"keyword": ["digo_cust_asset_in10000"]}, {}, {}
    )
    assert [item["metricName"] for item in by_field["data"]["data"]] == ["digo_cust_asset_in10000"]


def test_metric_dimension_all_makes_all_strategy_metrics_support_all_strategy_dimensions():
    definitions = aloudata_mock_server.load_fixture("analysis_view_query_by_name.json")
    requested_metrics = [
        metric_name
        for definition in definitions.values()
        for metric_name in definition["data"]["metrics"]
    ]
    dimension_directory = aloudata_mock_server.load_fixture("dimension_list.json")["data"]["data"]
    expected_dimensions = {dimension["dimName"] for dimension in dimension_directory}
    response = aloudata_mock_server.handle_dimension_all(
        {"metricNames": requested_metrics}, {}, {}
    )

    relations = response["data"]
    assert set(relations) == set(requested_metrics)
    assert all(set(dimensions) == expected_dimensions for dimensions in relations.values())


def test_dimension_list_supports_keyword_and_category_filtering():
    response = aloudata_mock_server.handle_dimension_list(
        {}, {"keyword": "转化指标名称", "categoryId": "dim-sub-strategy",
            "pager": {"pageNumber": 1, "pageSize": 10}}, {}
    )

    data = response["data"]
    assert data["total"] == 1
    assert data["data"][0]["dimName"] == "metric_name"
    assert data["data"][0]["dimDisplayName"] == "转化指标名称"


def test_hover_details_return_source_view_metadata():
    metric = aloudata_mock_server.handle_batch_detail(
        {"metricNames": ["digo_cust_asset_in"]}, {}, {}
    )
    dimension = aloudata_mock_server.handle_dimension_detail(
        {"dimName": ["metric_name"]}, {}, {}
    )

    assert metric["data"][0]["metricDisplayName"] == "入金客户数"
    assert dimension["data"]["dimDisplayName"] == "转化指标名称"
    assert dimension["data"]["dimDescription"] == "转化指标名称"
