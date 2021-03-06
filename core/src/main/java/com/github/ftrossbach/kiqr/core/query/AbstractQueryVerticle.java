/**
 * Copyright © 2017 Florian Troßbach (trossbach@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.ftrossbach.kiqr.core.query;

import com.github.ftrossbach.kiqr.commons.config.querymodel.requests.AbstractQuery;
import com.github.ftrossbach.kiqr.core.query.exceptions.ScalarValueNotFoundException;
import com.github.ftrossbach.kiqr.core.query.exceptions.SerdeNotFoundException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.errors.InvalidStateStoreException;

/**
 * Created by ftr on 05/03/2017.
 */
public class AbstractQueryVerticle extends AbstractKiqrVerticle{

    private static final Logger LOG = LoggerFactory.getLogger(AbstractQueryVerticle.class);
    protected final String instanceId;
    protected final KafkaStreams streams;

    public AbstractQueryVerticle(String instanceId, KafkaStreams streams) {
        this.streams = streams;
        this.instanceId = instanceId;
    }



    protected void execute(String addressPrefix, MappingFunction mapper){
        vertx.eventBus().consumer(addressPrefix + instanceId, msg -> {
            AbstractQuery query = null;
            try {
                query = (AbstractQuery) msg.body();

                Serde<Object> keySerde = getSerde(query.getKeySerde());
                Serde<Object> valueSerde = getSerde(query.getValueSerde());

                Object response = mapper.apply(query, keySerde, valueSerde);
                msg.reply(response);
            } catch (ScalarValueNotFoundException e){
                msg.fail(404, e.getMessage());
            } catch (SerdeNotFoundException e) {
                LOG.error("Serde not instantiable", e);
                msg.fail(400, e.getMessage());
            } catch (InvalidStateStoreException e) {
                LOG.error(String.format("Store %s not queriable. Could be due to wrong store type or rebalancing", query.getStoreName()));
                msg.fail(500, String.format("Store not available due to rebalancing or wrong store type"));
            } catch (RuntimeException e) {
                LOG.error("Unexpected exception", e);
                msg.fail(500, e.getMessage());
            }
        });
    }

    @FunctionalInterface
    protected interface MappingFunction{

        Object apply(AbstractQuery query, Serde<Object> keySerde, Serde<Object> valueSerde);
    }
}
