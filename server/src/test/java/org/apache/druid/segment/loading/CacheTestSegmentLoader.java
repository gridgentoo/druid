/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.segment.loading;

import org.apache.druid.segment.QueryableIndex;
import org.apache.druid.segment.ReferenceCountingSegment;
import org.apache.druid.segment.Segment;
import org.apache.druid.segment.SegmentLazyLoadFailCallback;
import org.apache.druid.segment.StorageAdapter;
import org.apache.druid.timeline.DataSegment;
import org.apache.druid.timeline.SegmentId;
import org.joda.time.Interval;

import java.util.concurrent.ExecutorService;

/**
*/
public class CacheTestSegmentLoader implements SegmentLoader
{

  @Override
  public ReferenceCountingSegment getSegment(final DataSegment segment, boolean lazy, SegmentLazyLoadFailCallback SegmentLazyLoadFailCallback)
  {
    Segment baseSegment = new Segment()
    {
      @Override
      public SegmentId getId()
      {
        return segment.getId();
      }

      @Override
      public Interval getDataInterval()
      {
        return segment.getInterval();
      }

      @Override
      public QueryableIndex asQueryableIndex()
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public StorageAdapter asStorageAdapter()
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close()
      {
      }
    };
    return ReferenceCountingSegment.wrapSegment(baseSegment, segment.getShardSpec());
  }

  @Override
  public void loadSegmentIntoPageCache(DataSegment segment, ExecutorService exec)
  {

  }

  @Override
  public void cleanup(DataSegment segment)
  {

  }
}
