import cypher
import matplotlib.pyplot as plt
data = cypher.run("MATCH (a)-[l:LINK]-(b) RETURN a, b, l", conn="http://neo4j:test123@localhost:7474/")
data.get_graph()
data.draw()
import time
plt.show()
while True:
    time.sleep(0.5)