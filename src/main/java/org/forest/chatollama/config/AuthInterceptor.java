package org.forest.chatollama.config;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.forest.chatollama.common.context.UserContext;
import org.forest.chatollama.common.exception.AuthException;
import org.forest.chatollama.dto.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final IepConfig iepConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("token");
        if (token == null || token.isBlank()) {
            throw new AuthException(401, "缺少token");
        }
        String url = iepConfig.getUrl() + "/SysUser/getCurrentUser";
        String resultStr;
        try {
             resultStr = HttpUtil.createGet(url).header("token", token).execute().body();
        }catch (Exception e){
            log.error("token请求失败", e);
            throw new AuthException(401, "token请求失败");
        }
        JSONObject result = JSONUtil.parseObj(resultStr);
        int code = result.getInt("code", -1);
        if (code != 200) {
            String msg = result.getStr("msg", "鉴权失败");
            throw new AuthException(code, msg);
        }
        JSONObject data = result.getJSONObject("data");
        User user = data.toBean(User.class);
        UserContext.setUser(user);
        UserContext.setToken(token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}