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


def test_metric_time_constraint_aggregates_across_dates_without_displayed_dimensions():
    response = aloudata_mock_server.handle_metrics_query(
        {},
        {
            "metrics": ["digo_distr_count_1", "digo_distr_user_cnt_a"],
            "dimensions": [],
            "timeConstraint": '([metric_time] >= "2026-09-01" AND [metric_time] < "2026-09-03")',
            "limit": 50,
            "offset": 0,
            "queryResultType": "DATA",
        },
        {},
    )

    data = response["data"]
    columns = data["table"]["columns"]
    assert response["success"] is True
    assert data["total"] == 1
    assert columns["digo_distr_count_1"][0]["value"] == 2940
    assert columns["digo_distr_user_cnt_a"][0]["value"] == 2430
    assert "metric_time" not in columns
