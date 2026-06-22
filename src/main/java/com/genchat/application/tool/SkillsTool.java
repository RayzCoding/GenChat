package com.genchat.application.tool;

import com.genchat.common.utils.MarkdownParser;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SkillsTool {
    private static final String TOOL_DESCRIPTION_TEMPLATE = """
			Load a skill in the current session. This tool's only purpose is to accept a skill name and return its full prompt and working directory.

			<What is a skill>
			A skill is a specialized prompt containing domain knowledge, workflows, and operating instructions.
			Each skill may also include reference files, templates, scripts, and other resources in its working directory.
			</What is a skill>

			<Full skill workflow>
			Step 1 — Decide whether a skill is needed:
			  When the user asks you to complete a task, check whether a matching skill exists in <Available Skills> below.
			  If a match exists, proceed to step 2; otherwise answer using your own capabilities.

			Step 2 — Load the skill via this tool:
			  Call this tool with the skill's name field value (name only, no extra parameters).
			  You will receive the skill working directory path and the full skill prompt content.

			Step 3 — Read and understand the skill prompt:
			  Read the returned prompt carefully and understand its workflow and requirements.

			Step 4 — Execute according to the skill prompt:
			  Follow the skill prompt's instructions and workflow strictly.
			  Act as if stepping into a role defined by the skill prompt.
			  Read and use reference files, templates, and scripts from the skill directory when needed.
			  Use other tools to perform the concrete operations required by the skill.
			</Full skill workflow>

			<Key concept: skills are not tools>
			Skills and tools are different concepts:
			- Tools: capabilities you can invoke directly, such as search or file reading
			- Skills: prompts/instructions loaded via this tool; you then act according to their guidance
			A skill is not a tool and must not be invoked as one. The correct flow is:
			load via this tool → read the prompt → follow its instructions using real tools
			</Key concept: skills are not tools>

			<Strict prohibitions>
			- Do not invoke a skill name as if it were a standalone tool
			- Do not pretend to know a skill's content without loading it through this tool
			- Do not invent or guess skill names that are not listed in <Available Skills>
			- Do not reload the same skill repeatedly (load once per conversation)
			- Do not ignore the skill prompt after loading and improvise on your own
			</Strict prohibitions>

			<Available Skills>
			%s
			</Available Skills>
			""";

    public record SkillsInput(
            @ToolParam(description = "The name of the skill to load (just the name, without any parameters), for example \"pptx\"") String command) {
    }
    public static class SkillsFunction implements Function<SkillsInput, String> {

        private final Map<String, Skill> skillsMap;

        public SkillsFunction(Map<String, Skill> skillsMap) {
            this.skillsMap = skillsMap;
        }

        @Override
        public String apply(SkillsInput input) {
            Skill skill = this.skillsMap.get(input.command());

            if (skill != null) {
                return "Skills dir: %s\n\n%s".formatted(skill.basePath(), skill.content());
            }

            String availableNames = String.join(", ", this.skillsMap.keySet());
            return "Not found skills: " + input.command() + ". Currently available skills: " + availableNames;
        }

    }
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        protected Builder() {
        }
        private final List<Skill> skills = new ArrayList<>();

        public ToolCallback build() {
            Assert.notEmpty(this.skills, "At least one skill catalog or resource must be configured.");

            var skillsXml = skills.stream().map(Skill::toXml).collect(Collectors.joining("\n"));
            return FunctionToolCallback.builder("Skill", new SkillsFunction(toSkillsMap(this.skills)))
                    .description(TOOL_DESCRIPTION_TEMPLATE.formatted(skillsXml))
                    .inputType(SkillsInput.class)
                    .build();
        }

        public Builder addSkillsDirectory(String skillsRootDirectory) {
            return this.addSkillsDirectories(List.of(skillsRootDirectory));
        }

        public Builder addSkillsDirectories(List<String> skillsRootDirectories) {
            for (String dir : skillsRootDirectories) {
                this.skills.addAll(loadDirectory(dir));
            }
            return this;
        }
    }

    public record Skill(String basePath, Map<String, Object> frontMatter, String content) {

        public String name() {
            Object name = this.frontMatter().get("name");
            return name != null ? name.toString() : "";
        }

        public String toXml() {
            String frontMatterXml = this.frontMatter()
                    .entrySet()
                    .stream()
                    .map(e -> "  <%s>%s</%s>".formatted(e.getKey(), e.getValue(), e.getKey()))
                    .collect(Collectors.joining("\n"));

            return "<skill>\n%s\n</skill>".formatted(frontMatterXml);
        }
    }

    private static List<Skill> loadDirectory(String skillsRootDirectory) {
        List<Skill> skills = new ArrayList<>();
        Path root = Path.of(skillsRootDirectory);

        if (!Files.isDirectory(root)) {
            return skills;
        }

        try {
            Files.walk(root, FileVisitOption.FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .forEach(skillFile -> {
                        try {
                            String markdown = Files.readString(skillFile, StandardCharsets.UTF_8);
                            MarkdownParser parser = new MarkdownParser(markdown);
                            skills.add(new Skill(skillFile.getParent().toAbsolutePath().toString(),
                                    parser.getFrontMatter(), parser.getContent()));
                        }
                        catch (IOException e) {
                            throw new RuntimeException("Skill file parsing failed: " + skillFile, e);
                        }
                    });
        }
        catch (IOException e) {
            throw new RuntimeException("Skill catalog scans failed: " + skillsRootDirectory, e);
        }

        return skills;
    }
    private static Map<String, Skill> toSkillsMap(List<Skill> skills) {
        Map<String, Skill> skillsMap = new HashMap<>();
        for (Skill skill : skills) {
            skillsMap.put(skill.name(), skill);
        }
        return skillsMap;
    }
}
