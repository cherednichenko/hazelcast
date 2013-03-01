/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.monitor.impl;

import com.hazelcast.monitor.LocalAtomicLongOperationStats;
import com.hazelcast.monitor.LocalAtomicLongStats;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;

import java.io.IOException;

public class LocalAtomicLongStatsImpl extends LocalInstanceStatsSupport<LocalAtomicLongOperationStats>
        implements LocalAtomicLongStats {

    private long creationTime;
    private long lastAccessTime;
    private long lastUpdateTime;
    private long totalModifiedOperations;
    private long totalNonModifiedOperations;

    @Override
    LocalAtomicLongOperationStats newOperationStatsInstance() {
        return new LocalAtomicLongOperationStatsImpl();
    }

    @Override
    void writeDataInternal(ObjectDataOutput out) throws IOException {
        out.writeLong(creationTime);
        out.writeLong(lastAccessTime);
        out.writeLong(lastUpdateTime);
        out.writeLong(totalModifiedOperations);
        out.writeLong(totalNonModifiedOperations);

    }

    @Override
    void readDataInternal(ObjectDataInput in) throws IOException {
        creationTime = in.readLong();
        lastAccessTime = in.readLong();
        lastUpdateTime = in.readLong();
        totalModifiedOperations = in.readLong();
        totalNonModifiedOperations = in.readLong();
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public long getTotalModifiedOperations() {
        return totalModifiedOperations;
    }

    public void setTotalModifiedOperations(long totalModifiedOperations) {
        this.totalModifiedOperations = totalModifiedOperations;
    }

    public long getTotalNonModifiedOperations() {
        return totalNonModifiedOperations;
    }

    public void setTotalNonModifiedOperations(long totalNonModifiedOperations) {
        this.totalNonModifiedOperations = totalNonModifiedOperations;
    }

    @Override
    public String toString() {
        return "LocalAtomicLongStatsImpl{" +
                "creationTime=" + creationTime +
                ", lastAccessTime=" + lastAccessTime +
                ", lastUpdateTime=" + lastUpdateTime +
                ", totalModifiedOperations=" + totalModifiedOperations +
                ", totalNonModifiedOperations=" + totalNonModifiedOperations +
                ", localAtomicLongOperationStats=" + getOperationStats() +
                '}';
    }
}
