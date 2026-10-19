package cn.genlite.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

class QuotedYamlTextTest {
    @Test void quotedTextPreservesValuesCommentsApostrophesAndEmojiOffsets() {
        String yaml="# 中文注释\ntitle: <#ABCDEF>📦 仓库\nlore:\n- <italic:false>玩家's 物品\n- '<#123456>关闭'\naction: close\nschema-version: 2\n";
        yaml+="shape:\n- OOOOOOOOO\n- '    X    '\n";
        String formatted=QuotedYamlText.format(yaml);
        assertEquals(new Yaml().<Object>load(yaml),new Yaml().<Object>load(formatted));
        assertTrue(formatted.contains("title: '<#ABCDEF>📦 仓库'"));
        assertTrue(formatted.contains("- '<italic:false>玩家''s 物品'"));
        assertTrue(formatted.contains("- 'OOOOOOOOO'"));
        assertTrue(formatted.contains("- '    X    '"));
        assertTrue(formatted.startsWith("# 中文注释\n"));assertEquals(formatted,QuotedYamlText.format(formatted));
    }
}
