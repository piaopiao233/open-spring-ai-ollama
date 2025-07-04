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
        ExcelReader excelReader = ExcelUtil.getReader("C:\\Users\\Admin\\Desktop\\1.xlsx");
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
        FileUtil.writeString(jsonStr, "C:\\Users\\Admin\\Desktop\\1.json", "utf-8");
    }

}
