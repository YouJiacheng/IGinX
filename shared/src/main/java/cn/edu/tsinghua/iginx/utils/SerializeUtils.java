/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package cn.edu.tsinghua.iginx.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerializeUtils {

    private static final Logger logger = LoggerFactory.getLogger(SerializeUtils.class);

    public static <T extends Serializable> byte[] serialize(T obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(bos)) {
            os.writeObject(obj);
        } catch (IOException e) {
            logger.error("encounter error when serialize: ", e);
        }
        return bos.toByteArray();
    }

    public static <T extends Serializable> T deserialize(byte[] data, Class<T> clazz) {
        ByteArrayInputStream bin = new ByteArrayInputStream(data);
        Object obj = null;
        try (ObjectInputStream in = new ObjectInputStream(bin)) {
            obj = in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.error("encounter error when deserialize: ", e);
        }
        if (obj == null) return null;
        return clazz.cast(obj);
    }
}
