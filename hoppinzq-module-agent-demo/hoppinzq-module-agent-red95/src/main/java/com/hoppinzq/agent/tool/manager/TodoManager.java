package com.hoppinzq.agent.tool.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppinzq.agent.tool.schema.TodoInput;
import com.hoppinzq.agent.tool.schema.TodoItem;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办事项管理器
 * 负责管理和跟踪待办事项列表，支持添加、更新和渲染待办事项。
 *
 * @author hoppinzq
 * @version 1.0
 */
public class TodoManager {

    /**
     * 待办事项列表
     */
    @Getter
    private final List<TodoItem> todos = new ArrayList<>();

    /**
     * 版本号，每次更新后自增
     */
    @Getter
    private long version = 0;

    /**
     * JSON 映射器，用于解析输入数据
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从 JSON 字符串更新待办事项
     *
     * @param input JSON 格式的待办事项输入
     * @return 更新后的待办事项渲染结果
     */
    public String updateTodos(String input) {
        try {
            TodoInput todoInput = mapper.readValue(input, TodoInput.class);
            return update(todoInput.getTodos());
        } catch (Exception e) {
            return "更新待办事项时出错: " + e.getMessage();
        }
    }

    /**
     * 更新待办事项列表
     * 验证输入的待办事项，检查内容、状态的有效性，并更新列表。
     * 限制最多 20 个待办事项，且同时只能有一个进行中的任务。
     *
     * @param items 待办事项列表
     * @return 更新后的待办事项渲染结果
     * @throws IllegalArgumentException 当输入参数不符合要求时抛出
     */
    public synchronized String update(List<TodoItem> items) {
        if (items == null) {
            items = new ArrayList<>();
        }

        List<TodoItem> validated = new ArrayList<>();
        int inProgressCount = 0;

        for (int i = 0; i < items.size(); i++) {
            TodoItem item = items.get(i);
            String content = item.getContent() != null ? item.getContent().trim() : "";
            String status = item.getStatus() != null ? item.getStatus().toLowerCase() : "pending";
            String activeForm = item.getActiveForm() != null && !item.getActiveForm().trim().isEmpty()
                ? item.getActiveForm().trim()
                : content;

            // 验证内容不能为空
            if (content.isEmpty()) {
                throw new IllegalArgumentException("第 " + i + " 项: 内容不能为空");
            }

            // 验证状态值是否有效
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("第 " + i + " 项: 无效的状态 '" + status + "'");
            }

            if (activeForm.isEmpty()) {
                activeForm = content;
            }

            // 统计进行中的任务数量
            if ("in_progress".equals(status)) {
                inProgressCount++;
            }

            item.setContent(content);
            item.setStatus(status);
            item.setActiveForm(activeForm);
            validated.add(item);
        }

        // 限制最多 20 个待办事项
        if (validated.size() > 20) {
            throw new IllegalArgumentException("最多支持 20 个待办事项");
        }

        // 限制同时只能有一个进行中的任务
        if (inProgressCount > 1) {
            throw new IllegalArgumentException("同时只能有一个进行中的任务");
        }

        this.todos.clear();
        this.todos.addAll(validated);
        this.version++;
        return render();
    }

    /**
     * 渲染待办事项列表为字符串
     * <p>
     * 使用不同符号标识不同状态的待办事项：
     * <ul>
     * <li>[ ] - 待处理</li>
     * <li>[>] - 进行中</li>
     * <li>[x] - 已完成</li>
     * </ul>
     * </p>
     *
     * @return 渲染后的待办事项字符串
     */
    public synchronized String render() {
        if (todos.isEmpty()) {
            return "暂无待办事项。";
        }

        StringBuilder sb = new StringBuilder();
        for (TodoItem item : todos) {
            String mark = "[?]";
            if ("completed".equals(item.getStatus())) {
                mark = "[x]";
            } else if ("in_progress".equals(item.getStatus())) {
                mark = "[>]";
            } else if ("pending".equals(item.getStatus())) {
                mark = "[ ]";
            }

            String suffix = "in_progress".equals(item.getStatus())
                ? " <- " + item.getActiveForm()
                : "";
            sb.append(String.format("%s %s%s%n", mark, item.getContent(), suffix));
        }

        long done = todos.stream().filter(t -> "completed".equals(t.getStatus())).count();
        sb.append(String.format("%n(%d/%d 已完成)", done, todos.size()));
        return sb.toString();
    }

    /**
     * 检查是否有未完成的待办事项
     *
     * @return 如果存在未完成的待办事项返回 true，否则返回 false
     */
    public synchronized boolean hasOpenItems() {
        return todos.stream().anyMatch(t -> !"completed".equals(t.getStatus()));
    }
}
