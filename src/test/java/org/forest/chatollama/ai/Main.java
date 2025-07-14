package org.forest.chatollama.ai;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ExcelReader excelReader = ExcelUtil.getReader("C:\\Users\\Admin\\Desktop\\项目\\1.xlsx");
        Sheet sheet = excelReader.getSheet();
        //获取sheet一共多少行
        int rowCount = sheet.getPhysicalNumberOfRows();
        List<ShareGptSession> shareGptSessions = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            //获取该行有多少列
            int columnCount = sheet.getRow(i).getPhysicalNumberOfCells();
            //一行是一个对话
            ShareGptSession shareGptSession = new ShareGptSession();
            List<Dialogue> conversations = new ArrayList<>();
            shareGptSession.setConversations(conversations);
            for (int j = 0; j < columnCount; j++) {
                //奇数列是用户角色 偶数列是gpt角色
                Dialogue dialogue = new Dialogue();
                if ((j + 1) % 2 == 0) {
                    dialogue.setRole("assistant");
                } else {
                    dialogue.setRole("user");
                }
                dialogue.setContent(sheet.getRow(i).getCell(j).getStringCellValue());
                conversations.add(dialogue);
            }
            shareGptSessions.add(shareGptSession);
        }

        String jsonStr = JSONUtil.toJsonStr(shareGptSessions);
        List<ShareGptSession> progressiveSessions = createProgressiveSessions(shareGptSessions);
        System.out.println(111);
        // FileUtil.writeString(jsonStr, "C:\\Users\\Admin\\Desktop\\1.json", "utf-8");
    }

    public static List<ShareGptSession> createProgressiveSessions(List<ShareGptSession> originalSessions) {
        List<ShareGptSession> progressiveSessions = new ArrayList<>();
        for (ShareGptSession session : originalSessions) {
            List<Dialogue> fullConversation = session.getConversations();
            int totalDialogues = fullConversation.size();
            // 跳过空对话
            if (totalDialogues < 2) continue;
            // 为每个长度创建渐进式对话
            for (int length = 2; length <= totalDialogues; length += 2) {
                // 创建当前长度的子对话（浅拷贝）
                List<Dialogue> subConversation = new ArrayList<>(fullConversation.subList(0, length));
                // 构建新会话对象
                ShareGptSession newSession = new ShareGptSession();
                newSession.setConversations(subConversation);
                progressiveSessions.add(newSession);
            }
        }
        return progressiveSessions;
    }

}
