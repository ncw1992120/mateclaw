import json

import pandas as pd
import pytest

import mateclaw
from runner.models import TaskRequest
from runner.worker import execute_task


def tools():
    assert hasattr(mateclaw, "diagnostics"), "Worker SDK must expose diagnostics"
    return mateclaw.diagnostics


def test_numeric_errors_identify_positions_even_with_duplicate_dataframe_index():
    frame = pd.DataFrame({"id": ["A", "B", "C"], "amount": ["12", "bad", None]}, index=[7, 7, 2])
    with pytest.raises(ValueError) as caught:
        tools().to_numeric(frame, dataset="input", field="amount", row_keys=["id"])
    detail = caught.value.as_dict()
    assert detail["errorCount"] == 2
    assert detail["issues"][0] == {"rowNumber": 2, "rowKeys": {"id": "B"}, "valuePreview": "'bad'", "reason": "无法转换为数字"}
    assert detail["issues"][1]["rowNumber"] == 3
    assert detail["issues"][1]["reason"] == "空值不允许"
    assert frame["amount"].tolist() == ["12", "bad", None]


def test_null_policies_and_integer_validation():
    frame = pd.DataFrame({"amount": ["12", None]})
    assert tools().to_integer(frame, dataset="input", field="amount", null_policy="fill", default=0).tolist() == [12, 0]
    kept = tools().to_numeric(frame, dataset="input", field="amount", null_policy="keep")
    assert kept.iloc[0] == 12 and pd.isna(kept.iloc[1])
    with pytest.raises(ValueError) as caught:
        tools().to_integer(pd.DataFrame({"amount": [1.5, "9223372036854775808"]}), dataset="input", field="amount")
    assert caught.value.as_dict()["errorCount"] == 2


def test_date_conversion_required_fields_and_bounded_samples():
    converted = tools().to_datetime(pd.DataFrame({"date": ["2026-10-13"]}), dataset="input", field="date", format="%Y-%m-%d")
    assert converted.iloc[0] == pd.Timestamp("2026-10-13")
    kept = tools().to_datetime(pd.DataFrame({"date": ["2026-10-13", None]}), dataset="input", field="date", null_policy="keep", format="%Y-%m-%d")
    assert kept.dt.year.iloc[0] == 2026 and pd.isna(kept.iloc[1])
    with pytest.raises(ValueError) as caught:
        tools().required(pd.DataFrame({"name": [None] * 12}), dataset="input", field="name")
    assert caught.value.as_dict()["errorCount"] == 12
    assert len(caught.value.as_dict()["issues"]) == 10
    with pytest.raises(ValueError) as caught:
        tools().to_datetime(pd.DataFrame({"date": ["bad"]}), dataset="input", field="date", format="%Y-%m-%d")
    assert caught.value.as_dict()["issues"][0]["reason"] == "无法转换为日期"


def test_worker_returns_user_call_line_and_redacts_token_in_structured_details():
    response = execute_task(TaskRequest(
        taskId="diagnostic", script="import pandas as pd\ndf = pd.DataFrame({'amount': ['secret-token']})\nresult = diagnostics.to_numeric(df, dataset='input', field='amount')",
        datasetReadEndpoint="http://localhost:18089/internal/read", readToken="secret-token",
    ))
    assert response.status == "FAILED"
    detail = json.loads(response.error)
    assert detail["errorDetails"]["scriptLine"] == 3
    assert detail["errorDetails"]["field"] == "amount"
    assert "secret-token" not in response.error
    assert "[REDACTED]" in response.error
    assert "user_script.py" in detail["traceback"]
