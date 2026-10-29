#!/usr/bin/env bash
# 内核同步校验：所有教学模块的内核文件必须与权威源（06-compact）逐字节一致。
# 内核 = client/ 整目录 + session/ 整目录 + 能力契约三接口 + ZQAgent + AgentCommandHandler。
# 漂移即报错并列出差异清单；可挂 git pre-commit / CI。
#
# 用法：bash bin/check-kernel-sync.sh
set -uo pipefail

cd "$(dirname "$0")/.."

REF_MODULE="hoppinzq-module-agent-06-compact"
PKG="src/main/java/com/hoppinzq/agent"

# 内核文件清单（相对 PKG）：目录用 find 展开，单文件直接校验
KERNEL_DIRS="client session"
KERNEL_FILES="capability/Capability.java capability/AgentHook.java capability/AgentCommand.java base/ZQAgent.java command/AgentCommandHandler.java"

MODULES=$(ls -d hoppinzq-module-agent-[0-9][0-9]-* 2>/dev/null | grep -v "$REF_MODULE" || true)
if [ -z "$MODULES" ]; then
    echo "没有找到其他教学模块，无需校验。"
    exit 0
fi

drift=0
check_one() {
    local rel="$1" mod="$2"
    local ref="$REF_MODULE/$PKG/$rel"
    local target="$mod/$PKG/$rel"
    if [ ! -f "$target" ]; then
        echo "缺失  $target（权威源: $REF_MODULE）"
        drift=$((drift + 1))
        return
    fi
    if ! cmp -s "$ref" "$target"; then
        echo "漂移  $target"
        drift=$((drift + 1))
    fi
}

for dir in $KERNEL_DIRS; do
    while IFS= read -r rel; do
        [ -z "$rel" ] && continue
        for mod in $MODULES; do
            check_one "$rel" "$mod"
        done
    done < <(cd "$REF_MODULE/$PKG" && find "$dir" -name "*.java" | sort)
done

for rel in $KERNEL_FILES; do
    for mod in $MODULES; do
        check_one "$rel" "$mod"
    done
done

if [ "$drift" -gt 0 ]; then
    echo ""
    echo "[FAIL] 发现 $drift 处内核漂移。请以 $REF_MODULE 为权威源同步，或确认该模块有意偏离。"
    exit 1
fi
echo "[OK] 内核同步校验通过（权威源: $REF_MODULE）"