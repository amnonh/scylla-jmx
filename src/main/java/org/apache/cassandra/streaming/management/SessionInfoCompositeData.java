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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2015 ScyllaDB
 *
 * Modified by ScyllaDB
 */

package org.apache.cassandra.streaming.management;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import org.apache.cassandra.streaming.ProgressInfo;
import org.apache.cassandra.streaming.SessionInfo;
import org.apache.cassandra.streaming.StreamSession;
import org.apache.cassandra.streaming.StreamSummary;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class SessionInfoCompositeData {
    private static final String[] ITEM_NAMES = new String[] { "planId", "peer", "connecting", "receivingSummaries",
            "sendingSummaries", "state", "receivingFiles", "sendingFiles", "sessionIndex" };
    private static final String[] ITEM_DESCS = new String[] { "Plan ID", "Session peer", "Connecting address",
            "Summaries of receiving data", "Summaries of sending data", "Current session state", "Receiving files",
            "Sending files", "Session index" };
    private static final OpenType<?>[] ITEM_TYPES;

    public static final CompositeType COMPOSITE_TYPE;
    static {
        try {
            ITEM_TYPES = new OpenType[] { SimpleType.STRING, SimpleType.STRING, SimpleType.STRING,
                    ArrayType.getArrayType(StreamSummaryCompositeData.COMPOSITE_TYPE),
                    ArrayType.getArrayType(StreamSummaryCompositeData.COMPOSITE_TYPE), SimpleType.STRING,
                    ArrayType.getArrayType(ProgressInfoCompositeData.COMPOSITE_TYPE),
                    ArrayType.getArrayType(ProgressInfoCompositeData.COMPOSITE_TYPE), SimpleType.INTEGER };
            COMPOSITE_TYPE = new CompositeType(SessionInfo.class.getName(), "SessionInfo", ITEM_NAMES, ITEM_DESCS,
                    ITEM_TYPES);
        } catch (OpenDataException e) {
            throw Throwables.propagate(e);
        }
    }

    public static CompositeData toCompositeData(final UUID planId, SessionInfo sessionInfo) {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put(ITEM_NAMES[0], planId.toString());
        valueMap.put(ITEM_NAMES[1], sessionInfo.peer.getHostAddress());
        valueMap.put(ITEM_NAMES[2], sessionInfo.connecting.getHostAddress());
        Function<StreamSummary, CompositeData> fromStreamSummary = input -> StreamSummaryCompositeData
                .toCompositeData(input);
        valueMap.put(ITEM_NAMES[3], toArrayOfCompositeData(sessionInfo.receivingSummaries, fromStreamSummary));
        valueMap.put(ITEM_NAMES[4], toArrayOfCompositeData(sessionInfo.sendingSummaries, fromStreamSummary));
        valueMap.put(ITEM_NAMES[5], sessionInfo.state.name());
        Function<ProgressInfo, CompositeData> fromProgressInfo = input -> ProgressInfoCompositeData
                .toCompositeData(planId, input);
        valueMap.put(ITEM_NAMES[6], toArrayOfCompositeData(sessionInfo.getReceivingFiles(), fromProgressInfo));
        valueMap.put(ITEM_NAMES[7], toArrayOfCompositeData(sessionInfo.getSendingFiles(), fromProgressInfo));
        valueMap.put(ITEM_NAMES[8], sessionInfo.sessionIndex);
        try {
            return new CompositeDataSupport(COMPOSITE_TYPE, valueMap);
        } catch (OpenDataException e) {
            throw Throwables.propagate(e);
        }
    }

    public static SessionInfo fromCompositeData(CompositeData cd) {
        assert cd.getCompositeType().equals(COMPOSITE_TYPE);

        Object[] values = cd.getAll(ITEM_NAMES);
        InetAddress peer, connecting;
        try {
            peer = InetAddress.getByName((String) values[1]);
            connecting = InetAddress.getByName((String) values[2]);
        } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
        Function<CompositeData, StreamSummary> toStreamSummary = input -> StreamSummaryCompositeData
                .fromCompositeData(input);
        SessionInfo info = new SessionInfo(peer, (int) values[8], connecting,
                fromArrayOfCompositeData((CompositeData[]) values[3], toStreamSummary),
                fromArrayOfCompositeData((CompositeData[]) values[4], toStreamSummary),
                StreamSession.State.valueOf((String) values[5]), new HashMap<String, ProgressInfo>(),
                new HashMap<String, ProgressInfo>());
        Function<CompositeData, ProgressInfo> toProgressInfo = input -> ProgressInfoCompositeData
                .fromCompositeData(input);
        for (ProgressInfo progress : fromArrayOfCompositeData((CompositeData[]) values[6], toProgressInfo)) {
            info.updateProgress(progress);
        }
        for (ProgressInfo progress : fromArrayOfCompositeData((CompositeData[]) values[7], toProgressInfo)) {
            info.updateProgress(progress);
        }
        return info;
    }

    private static <T> Collection<T> fromArrayOfCompositeData(CompositeData[] cds, Function<CompositeData, T> func) {
        return Lists.newArrayList(Iterables.transform(Arrays.asList(cds), func));
    }

    private static <T> CompositeData[] toArrayOfCompositeData(Collection<T> toConvert,
            Function<T, CompositeData> func) {
        if (toConvert == null) {
            toConvert = Sets.newHashSet();
        }
        CompositeData[] composites = new CompositeData[toConvert.size()];
        return Lists.newArrayList(Iterables.transform(toConvert, func)).toArray(composites);
    }
}
