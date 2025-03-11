package org.forest.chatollama.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.forest.chatollama.model.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SecurityInterceptor implements HandlerInterceptor {


    @Value("${system.aes-key}")
    private String aesKey;

    @Value("${system.aes-data}")
    private String aesData;


    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 模拟检查权限（实际应替换为你的逻辑，如校验 Token）
        String token = request.getHeader("token");
        if (StrUtil.isBlank(token) || !validateEncryptedData(token)) {
            // 返回 401 错误码和 JSON 响应
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Result result = Result.fail(401, "未授权", null);
            response.getWriter().write(JSONUtil.toJsonStr(result));
            return false; // 终止请求继续执行
        }
        return true; // 放行请求
    }

    public boolean validateEncryptedData(String encryptedData) {
        try {
            AES aes = SecureUtil.aes(aesKey.getBytes());
            // 解密数据
            String decrypted = aes.decryptStr(encryptedData);
            // 拆分数据和时间戳
            String[] parts = decrypted.split("\\|");
            if (parts.length != 2) {
                return false;
            }
            String data = parts[0];
            if (!aesData.equals(data)) {
                return false;
            }
            String timestampStr = parts[1];
            // 解析时间戳
            long timestamp;
            try {
                timestamp = Long.parseLong(timestampStr);
            } catch (NumberFormatException e) {
                return false;
            }
            // 获取当前时间
            long currentTime = System.currentTimeMillis();
            // 校验时间有效性
            return timestamp <= currentTime && (currentTime - timestamp) <= 60 * 1000;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String encryptWithTimestamp() {
        // 拼接原始数据和时间戳
        long timestamp = System.currentTimeMillis();
        String content =  aesData + "|" + timestamp;
        // 初始化AES加密器
        AES aes = SecureUtil.aes(aesKey.getBytes());
        // 加密并返回Base64编码字符串
        return aes.encryptBase64(content);
    }
}


