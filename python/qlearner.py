import math
import random

import gym
import numpy as np
import pandas as pd


# buckets = (3, 4, 8, 6)
# learning_rate = (0.05, 1, 250)
# discount_factor = (1, 1, 100)
# explore_rate = (0, 0.7, 200)

class QConfig:
    def __init__(self, buckets, learning_rate, discount_factor, explore_rate, debug=False):
        self.buckets = buckets
        self.learning_rate = learning_rate
        self.discount_factor = discount_factor
        self.explore_rate = explore_rate
        self.debug = debug

    def get_learning_rate(self, attempt_no):
        return self.get_adjusted_rate(self.learning_rate, attempt_no)

    def get_discount_factor(self, attempt_no):
        return self.get_adjusted_rate(self.discount_factor, attempt_no)

    def get_explore_rate(self, attempt_no):
        return self.get_adjusted_rate(self.explore_rate, attempt_no)

    def get_adjusted_rate(self, rate, attempt_no):
        return max(rate[0], min(rate[1], rate[1] - math.log10((attempt_no + 1) / rate[2])))


class QLearner:
    def __init__(self, q_config):
        self.environment = gym.make('CartPole-v1')
        self.attempt_no = 1
        self.upper_bounds = [
            self.environment.observation_space.high[0],  # 2-3 kubelki
            0.5,  # 2-3 kubelki
            self.environment.observation_space.high[2],  # 5-6 kubelkow
            math.radians(50)  # 5-6 kubelkow
        ]
        self.lower_bounds = [
            self.environment.observation_space.low[0],
            -0.5,
            self.environment.observation_space.low[2],
            -math.radians(50)
        ]

        self.q_config = q_config

        # Q-Table for each observation-action pair
        self.q_table = np.zeros(q_config.buckets + (2,))

        # @formatter:off
        self.cart_position_bins = \
            pd.cut([self.lower_bounds[0], self.upper_bounds[0]], bins=q_config.buckets[0], retbins=True)[1][1:-1]
        self.pole_angle_bins = \
            pd.cut([self.lower_bounds[1], self.upper_bounds[1]], bins=q_config.buckets[1], retbins=True)[1][1:-1]
        self.cart_velocity_bins = \
            pd.cut([self.lower_bounds[2], self.upper_bounds[2]], bins=q_config.buckets[2], retbins=True)[1][1:-1]
        self.angle_rate_bins = \
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
            new_observation, reward, done, info = self.environment.step(action)
            new_observation = self.discretise(new_observation)
            # reward = 0 if done else reward
            self.update_knowledge(action, observation, new_observation, reward, learning_rate, discount_factor)
            observation = new_observation
            reward_sum += reward

        # print("[%d] reward_sum: %s" % (self.attempt_no, reward_sum))
        if self.q_config.debug:
            print("%d, %.0f, %.2f, %.2f" % (self.attempt_no, reward_sum, learning_rate, explore_rate))

        self.attempt_no += 1
        return reward_sum

    def discretise(self, observation):
        # zamienia ciagle obserwacje na dyskretne kubelki
        # print(observation)
        cart_position, pole_angle, cart_velocity, angle_rate_of_change = observation
        return tuple([
            np.digitize(x=[cart_position], bins=self.cart_position_bins)[0],
            np.digitize(x=[pole_angle], bins=self.pole_angle_bins)[0],
            np.digitize(x=[cart_velocity], bins=self.cart_velocity_bins)[0],
            np.digitize(x=[angle_rate_of_change], bins=self.angle_rate_bins)[0],
        ])

    def pick_action(self, observation, explore_rate):
        # czy eksperymentujemy czy nie
        # albo losowo albo Q
        if random.random() < explore_rate:
            return self.environment.action_space.sample()
        else:
            return np.argmax(self.q_table[observation])

    def update_knowledge(self, action, observation, new_observation, reward, learning_rate, discount_factor):
        # odpowiedzialna za update stanu wiedzy (Q)
        observation_action = observation + (action,)
        qval = self.q_table[observation_action]
        maxqval = np.amax(self.q_table[new_observation])
        self.q_table[observation_action] += learning_rate * (
                reward + discount_factor * maxqval - qval)

        pass


def save_results(results):
    f = open('out/results.txt', 'w')
    f.write("\n".join(map(lambda x: "%.0f" % x, results)))
    f.close()
    pass


def main():
    attempts = 1000

    # cart_position, pole_angle, cart_velocity, angle_rate_of_change
    buckets = (3, 6, 8, 4)

    # Learning related constants (min, max, steps required to change)
    learning_rate = (0.2, 0.7, 200)
    discount_factor = (1, 1, 1)
    explore_rate = (0, 0.3, 100)

    config = QConfig(buckets, learning_rate, discount_factor, explore_rate, True)
    learner = QLearner(config)
    results = learner.learn(attempts)
    save_results(results)


if __name__ == '__main__':
    main()
