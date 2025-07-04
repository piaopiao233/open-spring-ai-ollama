package org.forest.chatollama.ai;

import lombok.Data;

import java.util.List;

@Data
public class ShareGptSession {

    private List<Dialogue> conversations;

}
