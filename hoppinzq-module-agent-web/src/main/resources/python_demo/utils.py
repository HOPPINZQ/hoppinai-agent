"""
工具函数模块 - 提供常用的 Python 工具函数

包含功能：
- 字符串处理工具
- 文件操作工具
- 数据格式转换工具
- 日期时间工具
"""

import json
import os
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Union


# ==================== 字符串工具 ====================

def reverse_string(s: str) -> str:
    """反转字符串"""
    return s[::-1]


def count_words(text: str) -> int:
    """统计文本中的单词数量"""
    return len(text.split())


def is_palindrome(s: str) -> bool:
    """判断字符串是否为回文（忽略大小写和空格）"""
    cleaned = ''.join(s.lower().split())
    return cleaned == cleaned[::-1]


def truncate_string(s: str, max_length: int, suffix: str = '...') -> str:
    """截断字符串到指定长度"""
    if len(s) <= max_length:
        return s
    return s[:max_length - len(suffix)] + suffix


def to_snake_case(camel_str: str) -> str:
    """驼峰命名转蛇形命名
    
    示例:
        >>> to_snake_case('helloWorld')
        'hello_world'
    """
    result = []
    for i, char in enumerate(camel_str):
        if char.isupper():
            if i > 0:
                result.append('_')
            result.append(char.lower())
        else:
            result.append(char)
    return ''.join(result)


# ==================== 文件操作工具 ====================

def read_file(filepath: str, encoding: str = 'utf-8') -> str:
    """读取文件内容"""
    with open(filepath, 'r', encoding=encoding) as f:
        return f.read()


def write_file(filepath: str, content: str, encoding: str = 'utf-8') -> None:
    """写入文件内容（自动创建目录）"""
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, 'w', encoding=encoding) as f:
        f.write(content)


def glob(directory: str, extension: Optional[str] = None) -> List[str]:
    """列出目录中的文件，可选按扩展名过滤"""
    files = []
    for f in os.listdir(directory):
        full_path = os.path.join(directory, f)
        if os.path.isfile(full_path):
            if extension is None or f.endswith(extension):
                files.append(f)
    return sorted(files)


def get_file_size(filepath: str) -> str:
    """获取文件大小（人类可读格式）"""
    size = os.path.getsize(filepath)
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size < 1024:
            return f'{size:.2f} {unit}'
        size /= 1024
    return f'{size:.2f} TB'


# ==================== 数据格式转换 ====================

def json_to_dict(json_str: str) -> Dict[str, Any]:
    """JSON 字符串转字典"""
    return json.loads(json_str)


def dict_to_json(data: Dict[str, Any], pretty: bool = False) -> str:
    """字典转 JSON 字符串"""
    if pretty:
        return json.dumps(data, ensure_ascii=False, indent=2)
    return json.dumps(data, ensure_ascii=False)


def flatten_dict(d: Dict[str, Any], parent_key: str = '', sep: str = '.') -> Dict[str, Any]:
    """展平嵌套字典
    
    示例:
        >>> flatten_dict({'a': {'b': 1, 'c': 2}})
        {'a.b': 1, 'a.c': 2}
    """
    items = []
    for k, v in d.items():
        new_key = f'{parent_key}{sep}{k}' if parent_key else k
        if isinstance(v, dict):
            items.extend(flatten_dict(v, new_key, sep=sep).items())
        else:
            items.append((new_key, v))
    return dict(items)


# ==================== 日期时间工具 ====================

def get_current_datetime() -> str:
    """获取当前日期时间字符串"""
    return datetime.now().strftime('%Y-%m-%d %H:%M:%S')


def days_between(date1: str, date2: str, fmt: str = '%Y-%m-%d') -> int:
    """计算两个日期之间的天数差"""
    d1 = datetime.strptime(date1, fmt)
    d2 = datetime.strptime(date2, fmt)
    return abs((d2 - d1).days)


def add_days(date_str: str, days: int, fmt: str = '%Y-%m-%d') -> str:
    """给日期加上/减去指定天数"""
    dt = datetime.strptime(date_str, fmt)
    result = dt + timedelta(days=days)
    return result.strftime(fmt)


def time_it(func):
    """装饰器：测量函数执行时间"""
    import functools
    import time

    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        start = time.perf_counter()
        result = func(*args, **kwargs)
        elapsed = time.perf_counter() - start
        print(f'[{func.__name__}] 执行耗时: {elapsed:.4f} 秒')
        return result

    return wrapper


# ==================== 数学工具 ====================

def fibonacci(n: int) -> List[int]:
    """生成斐波那契数列的前 n 项"""
    if n <= 0:
        return []
    if n == 1:
        return [0]
    
    fib = [0, 1]
    for i in range(2, n):
        fib.append(fib[i - 1] + fib[i - 2])
    return fib


def is_prime(n: int) -> bool:
    """判断是否为素数"""
    if n < 2:
        return False
    if n < 4:
        return True
    if n % 2 == 0 or n % 3 == 0:
        return False
    i = 5
    while i * i <= n:
        if n % i == 0 or n % (i + 2) == 0:
            return False
        i += 6
    return True


# ==================== 主函数示例 ====================

if __name__ == '__main__':
    # 演示各种工具函数的使用
    print('=' * 50)
    print('Python 工具函数演示')
    print('=' * 50)

    # 字符串工具演示
    print('\n--- 字符串工具 ---')
    print(f'reverse_string("Hello"): {reverse_string("Hello")}')
    print(f'count_words("Hello World Python"): {count_words("Hello World Python")}')
    print(f'is_palindrome("A man a plan a canal Panama"): {is_palindrome("A man a plan a canal Panama")}')
    print(f'to_snake_case("helloWorldTest"): {to_snake_case("helloWorldTest")}')

    # 数学工具演示
    print('\n--- 数学工具 ---')
    print(f'fibonacci(10): {fibonacci(10)}')
    print(f'is_prime(17): {is_prime(17)}')
    print(f'is_prime(20): {is_prime(20)}')

    # 日期工具演示
    print('\n--- 日期工具 ---')
    print(f'当前时间: {get_current_datetime()}')
    print(f'days_between("2024-01-01", "2024-12-31"): {days_between("2024-01-01", "2024-12-31")} 天')

    # 数据格式演示
    print('\n--- 数据格式 ---')
    sample = {'name': 'Alice', 'info': {'age': 30, 'city': 'Beijing'}}
    print(f'展平字典: {flatten_dict(sample)}')
    print(f'美观 JSON:\n{dict_to_json(sample, pretty=True)}')
