package com.hoppinzq.agent.tool.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hoppinzq.agent.constant.AIConstants.SKILL_PATH;

public class SkillLoader {
    private final Map<String, String> skills = new HashMap<>();

    public SkillLoader() {
        loadSkills();
    }

    private void loadSkills() {
        try {
            URL resourceUrl = getClass().getClassLoader().getResource(SKILL_PATH);
            if (resourceUrl == null) {
                return;
            }

            String protocol = resourceUrl.getProtocol();
            if ("file".equals(protocol)) {
                loadSkillsFromFileSystem(resourceUrl);
            } else if ("jar".equals(protocol)) {
                loadSkillsFromJar(resourceUrl);
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void loadSkillsFromFileSystem(URL resourceUrl) throws IOException, URISyntaxException {
        Path skillsPath = Paths.get(resourceUrl.toURI());
        try (Stream<Path> stream = Files.walk(skillsPath)) {
            stream.filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .forEach(this::parseSkill);
        }
    }

    private void loadSkillsFromJar(URL resourceUrl) throws IOException, URISyntaxException {
        String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(SKILL_PATH) && entryName.endsWith("SKILL.md")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        String content = new BufferedReader(new InputStreamReader(is))
                                .lines()
                                .collect(Collectors.joining("\n"));
                        parseSkillContent(entryName, content);
                    }
                }
            }
        }
    }

    private void parseSkill(Path path) {
        try {
            String content = Files.readString(path);
            String skillName = path.getParent().getFileName().toString();
            parseSkillContent(skillName, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseSkillContent(String entryName, String content) {
        Pattern pattern = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        String name = entryName;
        if (entryName.contains("/")) {
            int lastSlash = entryName.lastIndexOf("/");
            if (entryName.endsWith("/SKILL.md")) {
                name = entryName.substring(entryName.lastIndexOf("/", lastSlash - 1) + 1, lastSlash);
            } else {
                name = entryName.substring(0, entryName.indexOf("/"));
            }
        }

        String description = "";
        String body = content;

        if (matcher.find()) {
            String meta = matcher.group(1);
            body = matcher.group(2).trim();

            for (String line : meta.split("\n")) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts[0].trim().equals("name")) {
                        name = parts[1].trim();
                    } else if (parts[0].trim().equals("description")) {
                        description = parts[1].trim();
                    }
                }
            }
        }

        skills.put(name, String.format("<skill name=\"%s\" description=\"%s\">\n%s\n</skill>", name, description, body));
    }

    public String getSkill(String skillName) {
        if (!skills.containsKey(skillName)) {
            return "错误：未知技能 '" + skillName + "'。可用技能：" + String.join(", ", skills.keySet());
        }
        return skills.get(skillName);
    }

    public String getDescriptions() {
        if (skills.isEmpty()) return "（无可用技能）";
        StringBuilder sb = new StringBuilder();
        skills.forEach((name, body) -> {
            // 从存储的XML或元数据中提取描述（如果可能）
            // 为简单起见，这里只列出名称，或者解析存储在映射中的描述
            // 让我们重新解析XML或者单独存储描述
            // 匹配格式："  - {n}: {d}"
            String desc = "-";
            if (body.contains("description=\"")) {
                int start = body.indexOf("description=\"") + 13;
                int end = body.indexOf("\"", start);
                if (end > start) desc = body.substring(start, end);
            }
            sb.append(String.format("  - %s: %s\n", name, desc));
        });
        return sb.toString().trim();
    }

    public List<String> getAvailableSkills() {
        return new ArrayList<>(skills.keySet());
    }
}
