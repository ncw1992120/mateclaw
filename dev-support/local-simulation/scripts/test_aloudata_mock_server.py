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
