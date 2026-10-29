#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
工具函数测试模块 - 对 utils.py 中的函数进行单元测试
"""

import sys
import os
import unittest
from datetime import datetime

# 将父目录添加到系统路径，以便导入 utils 模块
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from utils import (
    reverse_string,
    count_words,
    is_palindrome,
    truncate_string,
    to_snake_case,
    fibonacci,
    is_prime,
    days_between,
    add_days,
    flatten_dict,
    json_to_dict,
    dict_to_json,
)


class TestStringUtils(unittest.TestCase):
    """字符串工具函数测试"""

    def test_reverse_string(self):
        """测试字符串反转"""
        self.assertEqual(reverse_string('hello'), 'olleh')
        self.assertEqual(reverse_string(''), '')
        self.assertEqual(reverse_string('a'), 'a')
        self.assertEqual(reverse_string('12345'), '54321')

    def test_count_words(self):
        """测试单词计数"""
        self.assertEqual(count_words('Hello World'), 2)
        self.assertEqual(count_words(''), 0)
        self.assertEqual(count_words('Python'), 1)
        self.assertEqual(count_words('a b c d e'), 5)

    def test_is_palindrome(self):
        """测试回文判断"""
        self.assertTrue(is_palindrome('A man a plan a canal Panama'))
        self.assertTrue(is_palindrome('racecar'))
        self.assertTrue(is_palindrome(''))
        self.assertFalse(is_palindrome('hello'))
        self.assertTrue(is_palindrome('上海自来水来自海上'))

    def test_truncate_string(self):
        """测试字符串截断"""
        self.assertEqual(truncate_string('Hello World', 5), 'Hello...')
        self.assertEqual(truncate_string('Hi', 5), 'Hi')
        self.assertEqual(truncate_string('Hello', 5, '***'), 'Hello***')

    def test_to_snake_case(self):
        """测试驼峰转蛇形"""
        self.assertEqual(to_snake_case('helloWorld'), 'hello_world')
        self.assertEqual(to_snake_case('HelloWorld'), 'hello_world')
        self.assertEqual(to_snake_case('hello'), 'hello')
        self.assertEqual(to_snake_case(''), '')


class TestMathUtils(unittest.TestCase):
    """数学工具函数测试"""

    def test_fibonacci(self):
        """测试斐波那契数列"""
        self.assertEqual(fibonacci(0), [])
        self.assertEqual(fibonacci(1), [0])
        self.assertEqual(fibonacci(2), [0, 1])
        self.assertEqual(fibonacci(5), [0, 1, 1, 2, 3])
        self.assertEqual(fibonacci(10), [0, 1, 1, 2, 3, 5, 8, 13, 21, 34])

    def test_is_prime(self):
        """测试素数判断"""
        self.assertFalse(is_prime(0))
        self.assertFalse(is_prime(1))
        self.assertTrue(is_prime(2))
        self.assertTrue(is_prime(3))
        self.assertFalse(is_prime(4))
        self.assertTrue(is_prime(17))
        self.assertFalse(is_prime(20))
        self.assertTrue(is_prime(97))
        self.assertFalse(is_prime(100))


class TestDateUtils(unittest.TestCase):
    """日期工具函数测试"""

    def test_days_between(self):
        """测试日期差计算"""
        self.assertEqual(days_between('2024-01-01', '2024-01-01'), 0)
        self.assertEqual(days_between('2024-01-01', '2024-01-02'), 1)
        self.assertEqual(days_between('2024-01-02', '2024-01-01'), 1)
        self.assertEqual(days_between('2024-01-01', '2024-12-31'), 365)

    def test_add_days(self):
        """测试日期加减"""
        self.assertEqual(add_days('2024-01-01', 1), '2024-01-02')
        self.assertEqual(add_days('2024-01-01', -1), '2023-12-31')
        self.assertEqual(add_days('2024-01-01', 0), '2024-01-01')
        self.assertEqual(add_days('2024-12-31', 1), '2025-01-01')


class TestDataFormatUtils(unittest.TestCase):
    """数据格式工具函数测试"""

    def test_json_to_dict(self):
        """测试 JSON 转字典"""
        result = json_to_dict('{"name": "Alice", "age": 30}')
        self.assertEqual(result, {'name': 'Alice', 'age': 30})
        
    def test_dict_to_json(self):
        """测试字典转 JSON"""
        data = {'name': 'Alice', 'age': 30}
        result = dict_to_json(data)
        self.assertIsInstance(result, str)
        self.assertIn('Alice', result)

    def test_flatten_dict(self):
        """测试字典展平"""
        nested = {'a': {'b': 1, 'c': 2}, 'd': 3}
        result = flatten_dict(nested)
        self.assertEqual(result, {'a.b': 1, 'a.c': 2, 'd': 3})
        
    def test_flatten_dict_empty(self):
        """测试空字典展平"""
        self.assertEqual(flatten_dict({}), {})


if __name__ == '__main__':
    unittest.main(verbosity=2)
