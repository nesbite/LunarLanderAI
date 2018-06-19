import gym
from gym.wrappers import Monitor
import itertools
import numpy as np
import os 
import random
import sys
import tensorflow as tf
from collections import deque
import paho.mqtt.client as mqtt
import time 
import json
import threading
import math
import pandas as pd

HOST = "192.168.1.2"

DEBUG = False

KEYEVENT_DPAD_LEFT = 21
KEYEVENT_DPAD_RIGHT = 22
KEYEVENT_SPACE = 62
KEYEVENT_NONE = 0

KEY_EVENT_MAPPING = {
    0: KEYEVENT_NONE,
    1: KEYEVENT_SPACE,
    2: KEYEVENT_DPAD_LEFT,
    3: KEYEVENT_DPAD_RIGHT
}

pub_topic = "DATA_FROM_AI"
sub_topic = "DATA_FROM_ANDROID"

class LunarEnv:
    def __init__(self):
        self.client = mqtt.Client("LunarLanderAI")
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message

        self.msg_event = threading.Event()
        self.connect_event = threading.Event()
        self.msg = None

    def connect(self):
        self.client.connect(HOST, port=1883, keepalive=60)
        self.client.loop_start()
        print("Waiting for connection...")
        self.connect_event.wait()

    def reset(self):
        self.client.publish(pub_topic, payload=str({'type': 'reset'}))
        if DEBUG: print("Waiting for next message from Android...")
        self.msg_event.wait()
        
        state = self.msg['state']
        return np.array([state['mX'], state['mY'], state['mDX'], state['mDY'], state['mHeading'], state['mOnGoal']])

    def step(self, action):
        self.client.publish(pub_topic, payload=str({
            'type': 'step',
            'action': KEY_EVENT_MAPPING[action]
        }))

        if DEBUG: print("Waiting for next message from Android...")
        self.msg_event.wait()

        state = self.msg['state']
        observation = np.array([state['mX'], state['mY'], state['mDX'], state['mDY'], state['mHeading'], state['mOnGoal']])

        return tuple([observation, self.msg['reward'], self.msg['done']])

    def on_connect(self, client, userdata, flags, rc):
        print("Connected with result code " + str(rc))
        self.connect_event.set()
        self.connect_event.clear()
        
        # Subscribing in on_connect() means that if we lose the connection and
        # reconnect then subscriptions will be renewed.n
        self.client.subscribe(sub_topic)


    def on_message(self, client, userdata, msg):
        if DEBUG: print("Message received: " + str(msg.payload) + ", topic: " + msg.topic)

        m_decode=str(msg.payload.decode("utf-8","ignore"))
        json_msg = json.loads(m_decode)

        self.msg = json_msg
        self.msg_event.set()
        self.msg_event.clear()

    def pick_random_action(self):
        return np.random.choice(len(KEY_EVENT_MAPPING), p=[0.05, 0.95])
        # return np.random.choice(len(KEY_EVENT_MAPPING), p=[0.3, 0.3, 0.1, 0.3])



H1=256  
H2=512
BATCH_SIZE=512
GAMMA=0.99
INITIAL_EPSILON=1
END_EPSILON=0.1
END_EPSILON2 = 0.01
REPLAY_SIZE=100000

log_dir=os.path.abspath('./logs/test_1')
save_dir=('logs/model')
monitor_dir='logs/monitor'
class DQN():
    def __init__(self):
        self.replay_buffer=deque()
        self.time_step=0
        self.epsilon=INITIAL_EPSILON
        self.state_dim=6
        self.action_dim=4

        
        
        self.create_network()
        self.create_training_method()
        
        self.saver=tf.train.Saver()
        
        self.session=tf.InteractiveSession()
        self.session.run(tf.global_variables_initializer())
        
        #merged=tf.summary.merge_all()
        
        self.writer=tf.summary.FileWriter(log_dir,self.session.graph)
        self.merged=tf.summary.merge_all()
            
        
    def create_network(self):
        self.state_input=tf.placeholder(tf.float32,[None,self.state_dim],name='x')
        self.keep_prob = tf.placeholder(tf.float32)

        self.W1=tf.get_variable("W1",[self.state_dim,H1],initializer=tf.random_uniform_initializer(0,1))
        self.W1_summary=tf.summary.histogram('W1_histogram',self.W1)

        
        self.b1 = tf.Variable(tf.constant(0.01, shape=[H1,]))
        layer1=tf.nn.relu(tf.matmul(self.state_input,self.W1)+self.b1)
        layer1 = tf.nn.dropout(layer1,self.keep_prob)
        
        self.W1_h=tf.get_variable("W2",[H1,H1],initializer=tf.random_uniform_initializer(0,1))
        self.W1_h_summary=tf.summary.histogram('W2_histogram',self.W1_h)
        self.b1_h = tf.Variable(tf.constant(0.01, shape=[H1,]))
        layer2=tf.nn.relu(tf.matmul(layer1,self.W1_h)+self.b1_h)
        layer2=tf.nn.dropout(layer2,self.keep_prob)
        
        self.W2_h=tf.get_variable("W3",[H1,H2],initializer=tf.random_uniform_initializer(0,1))
        self.W2_h_summary=tf.summary.histogram('W3_histogram',self.W2_h)
        self.b2_h = tf.Variable(tf.constant(0.01, shape=[H2,]))
        layer3=tf.nn.tanh(tf.matmul(layer2,self.W2_h)+self.b2_h)
        layer3=tf.nn.dropout(layer3,self.keep_prob)
        
        self.W2=tf.get_variable("W4",[H2,self.action_dim],initializer=tf.random_uniform_initializer(0,1))
        self.W2_summary=tf.summary.histogram('W4_histogram',self.W2)
        self.b2 = tf.Variable(tf.constant(0.01, shape=[self.action_dim,]))

        self.Q_value=tf.matmul(layer3,self.W2)
        
    def create_training_method(self):
        self.action_input=tf.placeholder(tf.float32,[None,self.action_dim])
        self.y_input=tf.placeholder(tf.float32,[None])
        Q_action=tf.reduce_sum(tf.multiply(self.Q_value,self.action_input),
                               reduction_indices=1)
        

        self.loss=tf.reduce_mean(tf.square(self.y_input-Q_action))
        self.loss_summary=tf.summary.scalar('loss',self.loss)
        self.optimizer=tf.train.AdamOptimizer(5e-5).minimize(self.loss)
                               
    
    def perceive(self,state,action,reward,next_state,done):
        one_hot_action=np.zeros(self.action_dim)
        one_hot_action[action]=1
        self.replay_buffer.append((state,one_hot_action,reward,
                                   next_state,done))
        if len(self.replay_buffer)>REPLAY_SIZE:
            self.replay_buffer.popleft()
            
        if len(self.replay_buffer)>BATCH_SIZE:
            self.train_network(1.0)
    
    def train_network(self,keep_prob):
        self.time_step+=1
        for _ in range(1):
            minibatch=random.sample(self.replay_buffer,BATCH_SIZE)
            state_batch=[data[0] for data in minibatch]
            action_batch=[data[1] for data in minibatch]
            reward_batch=[data[2] for data in minibatch]
            next_state_batch=[data[3] for data in minibatch]
        
            y_batch=[]
            Q_value_batch=self.Q_value.eval(feed_dict={self.state_input:next_state_batch,self.keep_prob:1.0})
            for i in range(0,BATCH_SIZE):
                done=minibatch[i][4]
                if done:
                    y_batch.append(reward_batch[i])
                else:
                    y_batch.append(reward_batch[i]+GAMMA*np.max(Q_value_batch[i]))
            feed_dict={self.y_input:y_batch,
                   self.action_input:action_batch,
                   self.state_input:state_batch,
                   self.keep_prob:keep_prob}
            summary,_=self.session.run([self.merged,self.optimizer],feed_dict)
            

        
        
        
    def save_network(self,direct):
        self.saver.save(self.session,direct)
        
    def read_network(self,direct):
        self.saver.restore(self.session,direct)
    def egreedy_action(self,state):
        Q_value=self.Q_value.eval(feed_dict={self.state_input:[state],self.keep_prob:1.0})[0]
        if random.random()<=self.epsilon:
            return random.randint(0,self.action_dim-1)
        else:
            return np.argmax(Q_value)
    # for test
    def action(self,state):
         return np.argmax(self.Q_value.eval(feed_dict={self.state_input:[state],self.keep_prob:1.0})[0])
    
    def observ(self,env):
        for episode in range(1000):
            state=env.reset()
            done = False
            total_reward = 0.0
            while not done:
                action = random.randint(0,self.action_dim-1)
                one_hot_action=np.zeros(self.action_dim)
                one_hot_action[action]=1    
                next_state,reward,done=env.step(action)
                total_reward += reward
                self.replay_buffer.append((state,one_hot_action,reward,
                                   next_state,done))
                state = next_state
                if len(self.replay_buffer)>REPLAY_SIZE:
                    self.replay_buffer.popleft()
            print('Episode: %d, reward: %f' % (episode, total_reward))


EPISODE=1000000
STEP = 1000
TEST = 100

lunar_env = LunarEnv()
lunar_env.connect()
env = lunar_env
agent=DQN()
agent.observ(env)
#env=Monitor(env,monitor_dir,force=True)
#agent.read_network(save_dir)
sum_of_reward=0

for episode in range(EPISODE):
    state=env.reset()
    if agent.epsilon>END_EPSILON:
        agent.epsilon-=(INITIAL_EPSILON-END_EPSILON)/2000
    elif agent.epsilon>END_EPSILON2:
        agent.epsilon-=(END_EPSILON-END_EPSILON2)/3000
    done = False
    total_per_reward = 0
    while not done:
        action=agent.egreedy_action(state)
        next_state,reward,done = env.step(action)
        agent.perceive(state,action,reward,next_state,done)
        state=next_state
        total_per_reward += reward
    sum_of_reward += total_per_reward
    print("%.0f, %f" % (episode, total_per_reward))
    if episode%100==0:
        agent.save_network(save_dir)
        print('average reward of last 100 episode:',sum_of_reward/100)
        if sum_of_reward/100>200:
            total_reward=0
            for i in range(TEST):  
                state=env.reset()
                done = False
                while not done:
                    action=agent.action(state)
                    state,reward,done=env.step(action)
                    total_reward+=reward
            ave_reward=total_reward/TEST    
            print('episode: ',episode,"Avarge Reward:",ave_reward)
            if ave_reward>=200:
                #env.close()
                break
        sum_of_reward = 0