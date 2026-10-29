#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Python 示例代码包

包含实用的工具函数模块和单元测试。

模块列表:
    - utils: 提供字符串处理、文件操作、数据格式转换、日期时间、数学计算等工具函数
    - tests: 单元测试模块
"""

from .utils import (
    reverse_string,
    count_words,
    is_palindrome,
    truncate_string,
    to_snake_case,
    read_file,
    write_file,
    glob,
    get_file_size,
    json_to_dict,
    dict_to_json,
    flatten_dict,
    get_current_datetime,
    days_between,
    add_days,
    fibonacci,
    is_prime,
)

__version__ = '1.0.0'
__author__ = 'Hoppinzq Agent'
__all__ = [
    'reverse_string',
    'count_words',
    'is_palindrome',
    'truncate_string',
    'to_snake_case',
    'read_file',
    'write_file',
    'glob',
    'get_file_size',
    'json_to_dict',
    'dict_to_json',
    'flatten_dict',
    'get_current_datetime',
    'days_between',
    'add_days',
    'fibonacci',
    'is_prime',
]
