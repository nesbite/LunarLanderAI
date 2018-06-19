import paho.mqtt.client as mqtt
import time 
import sys
import random
import json
import threading
import math
import numpy as np
import pandas as pd
from time import sleep

HOST = "192.168.1.14"

DEBUG = False

KEYEVENT_DPAD_LEFT = 21
KEYEVENT_DPAD_RIGHT = 22
KEYEVENT_SPACE = 62
KEYEVENT_NONE = 0

KEY_EVENT_MAPPING = {
    0: KEYEVENT_NONE,
    1: KEYEVENT_SPACE
    # 2: KEYEVENT_DPAD_LEFT,
    # 3: KEYEVENT_DPAD_RIGHT
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
        return tuple([state['mX'], state['mY'], state['mDY'], state['mHeading'],])

    def step(self, action):
        self.client.publish(pub_topic, payload=str({
            'type': 'step',
            'action': KEY_EVENT_MAPPING[action]
        }))

        if DEBUG: print("Waiting for next message from Android...")
        self.msg_event.wait()

        state = self.msg['state']
        observation = tuple([state['mX'], state['mY'], state['mDY'], state['mHeading'],])

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


class QLearner:
    def __init__(self, environment, q_config):
        self.environment = environment
        self.attempt_no = 1
        self.lower_bounds = [ 300, 0, -300, 0 ] # TODO do not hardcode
        self.upper_bounds = [ 500, 1300, 0, 360 ] # TODO do not hardcode
        # self.upper_bounds = [
        #     self.environment.observation_space.high[0],  # 2-3 kubelki
        #     0.5,  # 2-3 kubelki
        #     self.environment.observation_space.high[2],  # 5-6 kubelkow
        #     math.radians(50)  # 5-6 kubelkow
        # ]
        # self.lower_bounds = [
        #     self.environment.observation_space.low[0],
        #     -0.5,
        #     self.environment.observation_space.low[2],
        #     -math.radians(50)
        # ]

        self.q_config = q_config

        # Q-Table for each observation-action pair
        self.q_table = np.zeros(q_config.buckets + (len(KEY_EVENT_MAPPING),))

        # @formatter:off
        self.mX_bins = \
            pd.cut([self.lower_bounds[0], self.upper_bounds[0]], bins=q_config.buckets[0], retbins=True)[1][1:-1]
        self.mY_bins = \
            pd.cut([self.lower_bounds[1], self.upper_bounds[1]], bins=q_config.buckets[1], retbins=True)[1][1:-1]
        self.mDY_bins = \
            pd.cut([self.lower_bounds[2], self.upper_bounds[2]], bins=q_config.buckets[2], retbins=True)[1][1:-1]
        self.mHeading_bins = \
            pd.cut([self.lower_bounds[3], self.upper_bounds[3]], bins=q_config.buckets[3], retbins=True)[1][1:-1]
        # @formatter:on

    def learn(self, max_attempts):
        return [self.attempt() for _ in range(max_attempts)]

    def attempt(self):
        observation = self.discretise(self.environment.reset())
        done = False
        reward_sum = 0.0

        learning_rate = self.q_config.get_learning_rate(self.attempt_no)
        discount_factor = self.q_config.get_discount_factor(self.attempt_no)
        explore_rate = self.q_config.get_explore_rate(self.attempt_no)

        while not done:
            # self.environment.render()
            # sleep(0.51)
            action = self.pick_action(observation, explore_rate)
            new_observation, reward, done = self.environment.step(action)
            new_observation = self.discretise(new_observation)
            # reward = 0 if done else reward
            self.update_knowledge(action, observation, new_observation, reward, learning_rate, discount_factor)
            observation = new_observation
            reward_sum += reward

        # print("[%d] reward_sum: %s" % (self.attempt_no, reward_sum))
        print("%d, %.0f, %.2f, %.2f" % (self.attempt_no, reward_sum, learning_rate, explore_rate))

        self.attempt_no += 1
        return reward_sum

    def discretise(self, observation):
        # zamienia ciagle obserwacje na dyskretne kubelki
        # print(observation)
        mX, mY, mDY, mHeading = observation
        return tuple([
            np.digitize(x=[mX], bins=self.mX_bins)[0],
            np.digitize(x=[mY], bins=self.mY_bins)[0],
            np.digitize(x=[mDY], bins=self.mDY_bins)[0],
            np.digitize(x=[mHeading], bins=self.mHeading_bins)[0],
        ])

    def pick_action(self, observation, explore_rate):
        # czy eksperymentujemy czy nie
        # albo losowo albo Q
        if random.random() < explore_rate:
            return self.environment.pick_random_action()
        else:
            return np.argmax(self.q_table[observation])

    def update_knowledge(self, action, observation, new_observation, reward, learning_rate, discount_factor):
        # odpowiedzialna za update stanu wiedzy (Q)
        if DEBUG: print("at: {}, a: {}, o: {}, no: {}, r: {}, lr: {}, df: {}".format(self.attempt_no, action, observation, new_observation, reward, learning_rate, discount_factor))
        observation_action = observation + (action,)
        qval = self.q_table[observation_action]
        maxqval = np.amax(self.q_table[new_observation])
        self.q_table[observation_action] += learning_rate * (
                reward + discount_factor * maxqval - qval)

        pass

class QConfig:
    def __init__(self, buckets, learning_rate, discount_factor, explore_rate):
        self.buckets = buckets
        self.learning_rate = learning_rate
        self.discount_factor = discount_factor
        self.explore_rate = explore_rate

    def get_learning_rate(self, attempt_no):
        return self.get_adjusted_rate(self.learning_rate, attempt_no)

    def get_discount_factor(self, attempt_no):
        return self.get_adjusted_rate(self.discount_factor, attempt_no)

    def get_explore_rate(self, attempt_no):
        return self.get_adjusted_rate(self.explore_rate, attempt_no)

    def get_adjusted_rate(self, rate, attempt_no):
        return max(rate[0], min(rate[1], rate[1] - math.log10((attempt_no + 1) / rate[2])))

def main():
    attempts = 100

    # mX, mY, mDY, mHeading
    buckets = (2, 6, 4, 2)

    # Learning related constants (min, max, steps required to change)
    learning_rate = (0.2, 0.7, 80)
    discount_factor = (0.2, 0.4, 30)
    explore_rate = (0, 0.4, 50)

    lunar_env = LunarEnv()
    lunar_env.connect()

    config = QConfig(buckets, learning_rate, discount_factor, explore_rate)
    learner = QLearner(lunar_env, config)
    learner.learn(attempts)

if __name__ == '__main__':
    main()
