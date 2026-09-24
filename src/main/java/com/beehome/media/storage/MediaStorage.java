package com.beehome.media.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;

public interface MediaStorage {
    void store(String key, byte[] bytes) throws IOException;
    Resource load(String key) throws IOException;
    void delete(String key) throws IOException;
}
