import tensorflow as tf
from tensorflow.python.tools import freeze_graph
from dqn_lunar_lander import DQN

MODEL_PATH = "logs/model1/model"
MODEL_NAME = 'dqn'

input_graph_path = 'freeze_graph/checkpoint/' + MODEL_NAME+'.pbtxt'
checkpoint_path = 'freeze_graph/checkpoint/' +MODEL_NAME+'.ckpt'
restore_op_name = "save/restore_all"
filename_tensor_name = "save/Const:0"
output_frozen_graph_name = 'freeze_graph/models/frozen_'+MODEL_NAME+'_4.pb'

def main():
    dqn = DQN()
    dqn.read_network(MODEL_PATH)

    # write graph
    tf.train.write_graph(dqn.session.graph_def, '.', input_graph_path)  
    dqn.saver.save(dqn.session, save_path = checkpoint_path)

    # print all graph nodes 
    # [print(n.name) for n in tf.get_default_graph().as_graph_def().node]

    dqn.session.close()

    # freeze graph
    freeze_graph.freeze_graph(input_graph_path, input_saver="",
                            input_binary=False, input_checkpoint=checkpoint_path, 
                            output_node_names="MatMul_3", restore_op_name="save/restore_all",
                            filename_tensor_name="save/Const:0", 
                            output_graph=output_frozen_graph_name, clear_devices=True, initializer_nodes="")

if __name__ == '__main__':
    main()