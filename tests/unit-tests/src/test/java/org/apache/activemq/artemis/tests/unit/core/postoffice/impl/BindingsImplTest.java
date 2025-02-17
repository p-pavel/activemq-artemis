/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.unit.core.postoffice.impl;

import javax.transaction.xa.Xid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.impl.BindingsImpl;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.RefsOperation;
import org.apache.activemq.artemis.core.server.impl.RoutingContextImpl;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.selector.filter.Filterable;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Test;

public class BindingsImplTest extends ActiveMQTestBase {

   @Test
   public void testGetNextBindingWithLoadBalancingOnDemand() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(new SimpleString("a"));
      fake.filter = null;  // such that it wil match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.ON_DEMAND;
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(1, fake.routedCount.get());
   }

   @Test
   public void testGetNextBindingWithLoadBalancingOff() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(new SimpleString("a"));
      fake.filter = null;  // such that it wil match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.OFF;
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(0, fake.routedCount.get());
   }

   @Test
   public void testGetNextBindingWithLoadBalancingOffWithRedistribution() throws Exception {
      final FakeRemoteBinding fake = new FakeRemoteBinding(new SimpleString("a"));
      fake.filter = null;  // such that it wil match all messages
      fake.messageLoadBalancingType = MessageLoadBalancingType.OFF_WITH_REDISTRIBUTION;
      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.route(new CoreMessage(0, 100), new RoutingContextImpl(new FakeTransaction()));
      assertEquals(0, fake.routedCount.get());
   }

   @Test
   public void testRemoveWhileRouting() throws Exception {
      // It would require many iterations before getting a failure
      for (int i = 0; i < 500; i++) {
         internalTest(true);
      }
   }

   @Test
   public void testRemoveWhileRedistributing() throws Exception {
      // It would require many iterations before getting a failure
      for (int i = 0; i < 500; i++) {
         internalTest(false);
      }
   }

   private void internalTest(final boolean route) throws Exception {
      final FakeBinding fake = new FakeBinding(new SimpleString("a"));

      final Bindings bind = new BindingsImpl(null, null, new NullStorageManager(1000));
      bind.addBinding(fake);
      bind.addBinding(new FakeBinding(new SimpleString("a")));
      bind.addBinding(new FakeBinding(new SimpleString("a")));

      Thread t = new Thread() {
         @Override
         public void run() {
            try {
               bind.removeBindingByUniqueName(fake.getUniqueName());
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      };

      Queue queue = new FakeQueue(new SimpleString("a"));
      t.start();

      for (int i = 0; i < 100; i++) {
         if (route) {
            bind.route(new CoreMessage(i, 100), new RoutingContextImpl(new FakeTransaction()));
         } else {
            bind.redistribute(new CoreMessage(i, 100), queue, new RoutingContextImpl(new FakeTransaction()));
         }
      }
   }

   private final class FakeTransaction implements Transaction {

      @Override
      public Object getProtocolData() {
         return null;
      }

      @Override
      public void setProtocolData(Object data) {

      }

      @Override
      public void afterStore(TransactionOperation sync) {

      }

      @Override
      public void addOperation(final TransactionOperation sync) {

      }

      @Override
      public boolean isEffective() {
         return false;
      }

      @Override
      public boolean hasTimedOut(long currentTime, int defaultTimeout) {
         return false;
      }

      @Override
      public void commit() throws Exception {

      }

      @Override
      public boolean tryRollback() {
         return true;
      }

      @Override
      public void commit(final boolean onePhase) throws Exception {

      }

      @Override
      public long getCreateTime() {

         return 0;
      }

      @Override
      public long getID() {

         return 0;
      }

      @Override
      public Object getProperty(final int index) {

         return null;
      }

      @Override
      public boolean isContainsPersistent() {
         return false;
      }

      @Override
      public State getState() {

         return null;
      }

      @Override
      public Xid getXid() {
         return null;
      }

      @Override
      public void markAsRollbackOnly(final ActiveMQException exception) {

      }

      @Override
      public void prepare() throws Exception {

      }

      @Override
      public void putProperty(final int index, final Object property) {

      }

      public void removeOperation(final TransactionOperation sync) {

      }

      @Override
      public void resume() {

      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.transaction.Transaction#rollback()
       */
      @Override
      public void rollback() throws Exception {

      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.transaction.Transaction#setState(org.apache.activemq.artemis.core.transaction.Transaction.State)
       */
      @Override
      public void setState(final State state) {

      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.transaction.Transaction#suspend()
       */
      @Override
      public void suspend() {

      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.transaction.Transaction#getDistinctQueues()
       */
      public Set<Queue> getDistinctQueues() {
         return Collections.emptySet();
      }

      @Override
      public void setContainsPersistent() {

      }

      @Override
      public void setTimeout(int timeout) {

      }

      @Override
      public List<TransactionOperation> getAllOperations() {
         return null;
      }

      public void setWaitBeforeCommit(boolean waitBeforeCommit) {
      }

      @Override
      public RefsOperation createRefsOperation(Queue queue, AckReason reason) {
         // TODO Auto-generated method stub
         return null;
      }

      @Override
      public boolean hasTimedOut() {
         return false;
      }
   }

   private final class FakeFilter implements Filter {

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.filter.Filter#getFilterString()
       */
      @Override
      public SimpleString getFilterString() {
         return null;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.filter.Filter#match(org.apache.activemq.artemis.core.server.ServerMessage)
       */
      @Override
      public boolean match(final Message message) {
         return false;
      }

      @Override
      public boolean match(Map<String, String> map) {
         return false;

      }

      @Override
      public boolean match(Filterable filterable) {
         return false;
      }

   }

   private class FakeBinding implements Binding {

      Filter filter = new FakeFilter();
      AtomicInteger routedCount = new AtomicInteger();
      @Override
      public void close() throws Exception {

      }

      @Override
      public void unproposed(SimpleString groupID) {

      }

      final SimpleString name;
      final SimpleString uniqueName = SimpleString.toSimpleString(UUID.randomUUID().toString());

      FakeBinding(final SimpleString name) {
         this.name = name;
      }

      @Override
      public SimpleString getAddress() {
         return null;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#getBindable()
       */
      @Override
      public Bindable getBindable() {

         return null;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#getClusterName()
       */
      @Override
      public SimpleString getClusterName() {

         return null;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#getDistance()
       */
      @Override
      public int getDistance() {
         return 0;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#getFilter()
       */
      @Override
      public Filter getFilter() {
         return filter;
      }

      @Override
      public Long getID() {
         return Long.valueOf(0L);
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#getRoutingName()
       */
      @Override
      public SimpleString getRoutingName() {
         return name;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#getType()
       */
      @Override
      public BindingType getType() {

         return null;
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#getUniqueName()
       */
      @Override
      public SimpleString getUniqueName() {
         return uniqueName;
      }

      @Override
      public boolean isExclusive() {
         return false;
      }

      @Override
      public boolean isHighAcceptPriority(final Message message) {
         return false;
      }

      @Override
      public void route(final Message message, final RoutingContext context) throws Exception {
         routedCount.incrementAndGet();
      }

      /* (non-Javadoc)
       * @see org.apache.activemq.artemis.core.postoffice.Binding#toManagementString()
       */
      @Override
      public String toManagementString() {
         return null;
      }

      @Override
      public boolean isConnected() {
         return true;
      }

      @Override
      public void routeWithAck(Message message, RoutingContext context) {

      }

   }

   private final class FakeRemoteBinding extends FakeBinding implements RemoteQueueBinding  {
      MessageLoadBalancingType messageLoadBalancingType;
      FakeRemoteBinding(SimpleString name) {
         super(name);
      }

      @Override
      public boolean isLocal() {
         return false;
      }

      @Override
      public int consumerCount() {
         return 0;
      }

      @Override
      public Queue getQueue() {
         return null;
      }

      @Override
      public void addConsumer(SimpleString filterString) throws Exception {

      }

      @Override
      public void removeConsumer(SimpleString filterString) throws Exception {
      }

      @Override
      public void reset() {
      }

      @Override
      public void disconnect() {
      }

      @Override
      public void connect() {
      }

      @Override
      public long getRemoteQueueID() {
         return 0;
      }

      @Override
      public MessageLoadBalancingType getMessageLoadBalancingType() {
         return messageLoadBalancingType;
      }
   }
}
