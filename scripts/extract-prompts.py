#!/usr/bin/env python3
"""Extract text blocks from prompt Java sources into classpath markdown files."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROMPTS_DIR = ROOT / "src/main/resources/prompts"
JAVA_DIR = ROOT / "src/main/java/com/genchat/common/prompts"

EXTRACTIONS = {
    "PlanExecutePrompts.java": {
        "PLAN": "plan-execute/plan.md",
        "EXECUTE": "plan-execute/execute.md",
        "CRITIQUE": "plan-execute/critique.md",
        "COMPRESS": "plan-execute/compress.md",
        "SUMMARIZE": "plan-execute/summarize.md",
        "REQUIREMENT_CLARIFICATION": "plan-execute/requirement-clarification.md",
        "RESEARCH_TOPIC_GENERATION": "plan-execute/research-topic-generation.md",
    },
    "BaseAgentPrompts.java": {
        "ROLE_DEFINITION": "base/role-definition.md",
        "TOOL_CALLING_RULES": "base/tool-calling-rules.md",
        "FINAL_ANSWER_RULES": "base/final-answer-rules.md",
        "OUTPUT_SPECIFICATIONS": "base/output-specifications.md",
        "MANDATORY_REQUIREMENTS": "base/mandatory-requirements.md",
    },
    "PptBuilderPrompts.java": {
        "INTENT_RECOGNITION_PROMPT": "ppt/intent-recognition.md",
        "REQUIREMENT_PROMPT": "ppt/requirement.md",
    },
}

METHOD_TEMPLATES = {
    "PptBuilderPrompts.java": {
        "getOutlinePrompt": "ppt/outline.md",
        "getSearchInfoPrompt": "ppt/search-info.md",
        "getTemplateSelectionPrompt": "ppt/template-selection.md",
        "getSchemaGenerationPrompt": "ppt/schema-generation.md",
        "getSchemaModifyPrompt": "ppt/schema-modify.md",
        "getSummaryPrompt": "ppt/summary.md",
        "getModifySummaryPrompt": "ppt/modify-summary.md",
        "getFailurePrompt": "ppt/failure.md",
    },
    "ReactAgentPrompts.java": {
        "getReactAgentSystemPrompt": "react/react-agent-system.md",
        "getWebSearchPrompt": "react/web-search.md",
        "getFilePrompt": "react/file.md",
        "getSkillsPrompt": "react/skills.md",
        "getWebSearchBasePrompt": "react/web-search-base.md",
        "getFileBasePrompt": "react/file-base.md",
        "getRecommendPrompt": "react/recommend.md",
    },
    "BaseAgentPrompts.java": {
        "getSystemTimePrompt": "base/system-time.md",
    },
}


from typing import Optional


def extract_constant(text: str, name: str) -> Optional[str]:
    pattern = rf"public static final String {name}\s*=\s*\"\"\"(.*?)\"\"\";"
    match = re.search(pattern, text, re.DOTALL)
    return match.group(1) if match else None


def extract_method_template(text: str, method: str) -> Optional[str]:
    pattern = rf"public static(?: final)? String {method}\([^)]*\)\s*\{{\s*return\s*\"\"\"(.*?)\"\"\"\.formatted"
    match = re.search(pattern, text, re.DOTALL)
    if match:
        return match.group(1)
    pattern2 = rf"public static String {method}\(\)\s*\{{\s*return\s*\"\"\"(.*?)\"\"\";"
    match2 = re.search(pattern2, text, re.DOTALL)
    return match2.group(1) if match2 else None


def main() -> None:
    PROMPTS_DIR.mkdir(parents=True, exist_ok=True)
    for java_file, mapping in EXTRACTIONS.items():
        content = (JAVA_DIR / java_file).read_text(encoding="utf-8")
        for const_name, rel_path in mapping.items():
            block = extract_constant(content, const_name)
            if block is None:
                raise SystemExit(f"Missing constant {const_name} in {java_file}")
            path = PROMPTS_DIR / rel_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(block, encoding="utf-8")
            print(f"Wrote {rel_path}")

    for java_file, mapping in METHOD_TEMPLATES.items():
        content = (JAVA_DIR / java_file).read_text(encoding="utf-8")
        for method_name, rel_path in mapping.items():
            block = extract_method_template(content, method_name)
            if block is None:
                raise SystemExit(f"Missing method template {method_name} in {java_file}")
            path = PROMPTS_DIR / rel_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(block, encoding="utf-8")
            print(f"Wrote {rel_path}")


if __name__ == "__main__":
    main()
