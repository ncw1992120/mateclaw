import pandas as pd
import pytest

from mateclaw.results import ResultContractError, normalize_result, message_result


def test_dataframe_normalizes_to_table_envelope():
    envelope = normalize_result(pd.DataFrame([{"amount": 12.5}]))
    assert envelope["kind"] == "table"
    assert envelope["data"]["columns"] == [
        {"name": "amount", "title": "amount", "dataType": "number", "nullable": False}
    ]
    assert envelope["data"]["rows"] == [{"amount": 12.5}]


def test_list_of_records_normalizes_to_table_envelope():
    envelope = normalize_result([{"id": 1}, {"id": 2}])
    assert envelope["meta"]["rowCount"] == 2
    assert envelope["data"]["rows"][1]["id"] == 2


def test_scalar_normalizes_to_scalar_envelope():
    assert normalize_result(42)["data"] == {"value": 42, "dataType": "number"}
    assert normalize_result("done")["data"] == {"value": "done", "dataType": "string"}
    assert normalize_result(True)["data"]["dataType"] == "boolean"


def test_empty_list_is_valid_empty_table():
    envelope = normalize_result([])
    assert envelope["kind"] == "table"
    assert envelope["data"] == {"columns": [], "rows": []}


def test_duplicate_dataframe_columns_are_rejected_with_path():
    value = pd.DataFrame([[1, 2]], columns=["id", "id"])
    with pytest.raises(ResultContractError, match="result.data.columns"):
        normalize_result(value)


def test_nan_and_nat_become_null_but_infinity_is_rejected():
    envelope = normalize_result(pd.DataFrame([{"score": float("nan"), "day": pd.NaT}]))
    assert envelope["data"]["rows"] == [{"score": None, "day": None}]
    with pytest.raises(ResultContractError, match=r"result\.data\.rows\[0\]\.score"):
        normalize_result([{"score": float("inf")}])
    assert envelope["data"]["columns"][0]["nullable"] is True


def test_mixed_incompatible_column_types_are_rejected():
    with pytest.raises(ResultContractError, match=r"result\.data\.rows\[1\]\.amount"):
        normalize_result([{"amount": 1}, {"amount": "unknown"}])


def test_unknown_object_is_not_stringified():
    with pytest.raises(ResultContractError, match="result"):
        normalize_result(object())


def test_explicit_message_envelope_is_validated():
    envelope = normalize_result(message_result("没有满足条件的数据", "info"))
    assert envelope["kind"] == "message"
    assert envelope["data"]["message"] == "没有满足条件的数据"


def test_message_rejects_empty_content_and_bad_level():
    with pytest.raises(ResultContractError, match="result.data.message"):
        normalize_result(message_result(""))
    with pytest.raises(ResultContractError, match="result.data.level"):
        normalize_result(message_result("x", "error"))


def test_none_result_is_contract_error():
    with pytest.raises(ResultContractError, match="result"):
        normalize_result(None)


def test_list_with_non_object_row_is_rejected_with_path():
    with pytest.raises(ResultContractError, match=r"result\.data\.rows\[1\]"):
        normalize_result([{"id": 1}, 42])


def test_datetime_values_become_iso_strings():
    from datetime import datetime, date
    envelope = normalize_result([{"day": date(2026, 9, 21), "at": datetime(2026, 9, 21, 10, 0, 0)}])
    assert envelope["data"]["rows"] == [{"day": "2026-09-21", "at": "2026-09-21T10:00:00"}]
    types = {column["name"]: column["dataType"] for column in envelope["data"]["columns"]}
    assert types == {"day": "date", "at": "datetime"}


def test_meta_contains_source_inputs_and_truncated_flag():
    envelope = normalize_result([{"id": 1}], source_inputs=("dataset_a",))
    assert envelope["meta"]["sourceInputs"] == ["dataset_a"]
    assert envelope["meta"]["truncated"] is False
