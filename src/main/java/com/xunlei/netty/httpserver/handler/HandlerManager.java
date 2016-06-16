package com.xunlei.netty.httpserver.handler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ZengDong
 * @since 2011-3-18 下午06:25:29
 */
public abstract class HandlerManager<T extends Handler> {

    private List<T> handlerChain = new ArrayList<T>(1);

    public void addFirst(T handler) {
        handlerChain.add(0, handler);
    }

    public void addLast(T handler) {
        handlerChain.add(handler);
    }

    public T removeFirst() {
        if (handlerChain.isEmpty()) {
            return null;
        }
        return handlerChain.remove(0);
    }

    public T removeLast() {
        if (handlerChain.isEmpty()) {
            return null;
        }
        return handlerChain.remove(handlerChain.size() - 1);
    }

    public List<T> getHandlerChain() {
        return handlerChain;
    }
}
