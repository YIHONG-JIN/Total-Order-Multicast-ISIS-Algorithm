This is MP1 readme to instruct how to run our program. The program are in the src folder

Make sure the jvm version is openjdk version "18.0.2.1" to promise the javac.

To compile, javac  *.java.

To clean, rm $(find . -name "*.class")

To run across three nodes, run the following commands
python3 -u gentx.py 0.5 | java mp1_node node1 1231 config1.txt &
python3 -u gentx.py 0.5 | java mp1_node node2 1232 config2.txt &
python3 -u gentx.py 0.5 | java mp1_node node3 1233 config3.txt


To run across eight nodes, run the following commands
python3 -u gentx.py 0.5 | java mp1_node node1 1231 config1.txt &
python3 -u gentx.py 0.5 | java mp1_node node2 1232 config2.txt &
python3 -u gentx.py 0.5 | java mp1_node node3 1233 config3.txt &
python3 -u gentx.py 0.5 | java mp1_node node4 1234 config4.txt &
python3 -u gentx.py 0.5 | java mp1_node node5 1235 config5.txt &
python3 -u gentx.py 0.5 | java mp1_node node6 1236 config6.txt &
python3 -u gentx.py 0.5 | java mp1_node node7 1237 config7.txt &
python3 -u gentx.py 0.5 | java mp1_node node8 1238 config8.txt 