package com.what.deploy.controller;

import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.what.deploy.util.OSUtil;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/terminal")
public class TerminalController {

    @GetMapping("/info")
    public SaResult getTerminalInfo() {
        Map<String, Object> info = new HashMap<>();
        boolean isWin = OSUtil.isWindows();
        boolean isLinux = OSUtil.isLinux();

        String userHome = System.getProperty("user.home");
        info.put("os", OSUtil.getOS());
        info.put("isWindows", isWin);
        info.put("isLinux", isLinux);
        info.put("userHome", userHome);
        info.put("currentDir", userHome);
        info.put("defaultDir", "~");

        List<Map<String, String>> shells = new ArrayList<>();
        if (isWin) {
            Map<String, String> pwsh = new HashMap<>();
            pwsh.put("name", "PowerShell");
            pwsh.put("value", "powershell");
            pwsh.put("isDefault", "true");
            shells.add(pwsh);

            Map<String, String> cmd = new HashMap<>();
            cmd.put("name", "Command Prompt (CMD)");
            cmd.put("value", "cmd");
            cmd.put("isDefault", "false");
            shells.add(cmd);
        } else {
            Map<String, String> bash = new HashMap<>();
            bash.put("name", "Bash (/bin/bash)");
            bash.put("value", "bash");
            bash.put("isDefault", "true");
            shells.add(bash);

            Map<String, String> sh = new HashMap<>();
            sh.put("name", "Sh (/bin/sh)");
            sh.put("value", "sh");
            sh.put("isDefault", "false");
            shells.add(sh);
        }
        info.put("shells", shells);

        // 默认编码建议
        if (isWin) {
            info.put("defaultCharset", System.getProperty("sun.jnu.encoding", "GBK"));
        } else {
            info.put("defaultCharset", "UTF-8");
        }

        return SaResult.data(info);
    }

    @PostMapping("/exec")
    public SaResult executeCommand(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        String dir = body.get("dir");
        if (StrUtil.isBlank(command)) {
            return SaResult.error("命令不能为空");
        }

        try {
            File workDir = null;
            if (StrUtil.isNotBlank(dir)) {
                File d = new File(dir);
                if (d.exists() && d.isDirectory()) {
                    workDir = d;
                }
            }

            ProcessBuilder pb;
            if (OSUtil.isWindows()) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            if (workDir != null) {
                pb.directory(workDir);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return SaResult.error("命令执行超时 (限制15秒)，已强行终止");
            }
            String output = RuntimeUtil.getResult(p);
            return SaResult.data(output);
        } catch (Exception e) {
            return SaResult.error("执行失败: " + e.getMessage());
        }
    }

    @PostMapping("/complete")
    public SaResult complete(@RequestBody Map<String, Object> body) {
        String dir = (String) body.get("dir");
        String line = (String) body.get("line");
        Integer cursorPos = (Integer) body.get("cursorPos");
        if (line == null) line = "";
        if (cursorPos == null || cursorPos > line.length()) cursorPos = line.length();

        String beforeCursor = line.substring(0, cursorPos);
        
        // 检查当前光标前是否有未闭合的引号（' 或 "）
        int unclosedQuoteIdx = -1;
        char quoteChar = 0;
        for (int i = 0; i < beforeCursor.length(); i++) {
            char c = beforeCursor.charAt(i);
            if (c == '\'' || c == '"') {
                if (quoteChar == 0) {
                    quoteChar = c;
                    unclosedQuoteIdx = i;
                } else if (quoteChar == c) {
                    quoteChar = 0;
                    unclosedQuoteIdx = -1;
                }
            }
        }

        boolean hasOpenQuote = (unclosedQuoteIdx != -1);
        int tokenStartIndex;
        String lastToken;

        if (hasOpenQuote) {
            tokenStartIndex = unclosedQuoteIdx;
            // token 包含引号后的实际输入文本
            lastToken = beforeCursor.substring(unclosedQuoteIdx + 1);
        } else {
            int lastSpace = Math.max(beforeCursor.lastIndexOf(' '), Math.max(beforeCursor.lastIndexOf('\t'), beforeCursor.lastIndexOf(';')));
            tokenStartIndex = (lastSpace == -1 ? 0 : lastSpace + 1);
            lastToken = beforeCursor.substring(tokenStartIndex);
        }

        // 解析当前有效工作目录
        String userHome = System.getProperty("user.home");
        File workDir = null;
        if (StrUtil.isNotBlank(dir)) {
            String resolvedDir = dir.trim();
            if (resolvedDir.equals("~")) {
                resolvedDir = userHome;
            } else if (resolvedDir.startsWith("~/") || resolvedDir.startsWith("~\\")) {
                resolvedDir = userHome + resolvedDir.substring(1);
            }
            File target = new File(resolvedDir);
            if (target.exists() && target.isDirectory()) {
                workDir = target;
            }
        }
        if (workDir == null) {
            workDir = new File(userHome);
        }

        List<String> candidates = new ArrayList<>();

        // 1. 若为命令第一个单词且未加引号，匹配常用 Shell 指令
        if (tokenStartIndex == 0 && !hasOpenQuote && StrUtil.isNotBlank(lastToken)) {
            List<String> commonCommands = Arrays.asList(
                "cd", "dir", "ls", "cls", "clear", "mkdir", "rm", "rmdir", "del", "copy", "move", "ren",
                "cat", "type", "echo", "ipconfig", "ifconfig", "ping", "netstat", "systeminfo", "tasklist",
                "taskkill", "ps", "top", "kill", "java", "javac", "jar", "mvn", "npm", "node", "yarn", "pnpm",
                "git", "docker", "docker-compose", "curl", "wget", "tar", "unzip", "chmod", "chown", "sudo",
                "grep", "findstr", "powershell", "cmd", "bash", "sh", "exit", "help", "nginx", "systemctl"
            );
            for (String cmd : commonCommands) {
                if (cmd.toLowerCase().startsWith(lastToken.toLowerCase())) {
                    candidates.add(cmd);
                }
            }
        }

        // 2. 匹配当前目录或相对/绝对路径下的文件与文件夹
        try {
            String searchDirStr;
            String searchPrefix;

            String tokenPath = lastToken;
            if (tokenPath.startsWith("~")) {
                tokenPath = userHome + tokenPath.substring(1);
            }

            int lastSep = Math.max(tokenPath.lastIndexOf('/'), tokenPath.lastIndexOf('\\'));
            if (lastSep != -1) {
                String dirPart = tokenPath.substring(0, lastSep + 1);
                searchPrefix = tokenPath.substring(lastSep + 1);
                File d = new File(dirPart);
                if (!d.isAbsolute()) {
                    d = new File(workDir, dirPart);
                }
                searchDirStr = d.getAbsolutePath();
            } else {
                searchDirStr = workDir.getAbsolutePath();
                searchPrefix = tokenPath;
            }

            File targetFolder = new File(searchDirStr);
            if (targetFolder.exists() && targetFolder.isDirectory()) {
                File[] files = targetFolder.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (candidates.size() >= 50) {
                            break;
                        }
                        if (StrUtil.isBlank(searchPrefix) || f.getName().toLowerCase().startsWith(searchPrefix.toLowerCase())) {
                            String match = f.getName();
                            if (f.isDirectory()) {
                                match += (OSUtil.isWindows() ? "\\" : "/");
                            }

                            String fullPath;
                            if (lastSep != -1) {
                                String dirPart = lastToken.substring(0, lastSep + 1);
                                fullPath = dirPart + match;
                            } else {
                                fullPath = match;
                            }

                            // 如果文件名/路径含有空格或特殊字符，进行引号包裹
                            if (hasOpenQuote) {
                                fullPath = String.valueOf(quoteChar) + fullPath + (fullPath.endsWith("\\") || fullPath.endsWith("/") ? "" : String.valueOf(quoteChar));
                            } else if (fullPath.contains(" ")) {
                                fullPath = "'" + fullPath + "'";
                            }

                            candidates.add(fullPath);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> res = new HashMap<>();
        res.put("lastToken", lastToken);
        res.put("tokenStartIndex", tokenStartIndex);
        res.put("candidates", candidates);
        return SaResult.data(res);
    }
}
