class Params(dict):
    """只读语义由调用方约束；用于脚本显式传递页面/任务参数。"""
    def require(self, name):
        if name not in self: raise KeyError(f"missing parameter: {name}")
        return self[name]
