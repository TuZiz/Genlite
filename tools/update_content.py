"""重新生成当前三维模型；材料与配方直接维护在 configuration/，不恢复退役工业材料链。"""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name('redesign_models.py')),run_name='__main__')
