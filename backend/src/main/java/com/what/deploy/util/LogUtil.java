package com.what.deploy.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存高效型日志读取工具类
 * 采用 RandomAccessFile 实现从文件末尾向前检索，避免大文件读取导致的 OOM
 */
public class LogUtil {

    /**
     * 读取文件的最后 N 行
     *
     * @param file     要读取的文件
     * @param numLines 读取的行数
     * @return 最后 N 行的列表
     */
    public static List<String> readLastLines(File file, int numLines) {
        if (file == null || !file.exists() || !file.isFile() || numLines <= 0) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length == 0) {
                return result;
            }

            long pos = length - 1;
            int linesFound = 0;
            StringBuilder sb = new StringBuilder();

            // 从文件末尾开始向前扫描
            while (pos >= 0 && linesFound < numLines) {
                raf.seek(pos);
                int c = raf.read();
                
                if (c == '\n') {
                    // 遇到换行符，且缓冲区有内容，则说明读取完了一行（从后往前看）
                    // 忽略文件最后可能存在的空换行
                    if (pos != length - 1) {
                        result.add(0, decode(sb));
                        sb.setLength(0);
                        linesFound++;
                    }
                } else if (c != '\r') {
                    // Hutool Tailer 或 Linux 通常以 \n 结尾，跳过 \r
                    sb.append((char) c);
                }

                pos--;
            }

            // 处理文件开头的第一行（如果没有扫描到换行符就到头了）
            if (sb.length() > 0 && linesFound < numLines) {
                result.add(0, decode(sb));
            }
        } catch (IOException e) {
            // 发生异常时返回空列表或已读取的部分
        }
        return result;
    }

    /**
     * 由于 StringBuilder 从后向前追加字符再反转效率通常高于插入开头，
     * 这里简单处理：RandomAccessFile 读取字节后需要注意编码问题。
     * 为了极致性能且日志通常为 ASCII，直接强转 char 在 UTF-8 下可能乱码，
     * 但此处为了规避 OOM 是首要任务。
     */
    private static String decode(StringBuilder sb) {
        // StringBuilder 反转后即为正常的行内容
        String raw = sb.reverse().toString();
        // 简单处理：将 ISO_8859_1 字节流转回 UTF-8 字符串的高效方式
        byte[] bytes = raw.getBytes(StandardCharsets.ISO_8859_1);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
