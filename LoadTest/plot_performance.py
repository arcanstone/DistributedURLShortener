import pandas as pd
import matplotlib.pyplot as plt

# Load the performance data from CSV
data = pd.read_csv('performance_data.csv')

# Separate the data by test type
load1_data = data[data['TestID'].str.contains('Load1')]
load2_data = data[data['TestID'].str.contains('Load2')]

# Plot Load1 (PUT requests) performance
plt.figure(figsize=(10, 6))
plt.plot(load1_data['TestID'], load1_data['ResponseTime(ms)'], label='Load1 (PUT requests)', marker='o')
plt.plot(load2_data['TestID'], load2_data['ResponseTime(ms)'], label='Load2 (GET requests)', marker='o')

# Customize the graph
plt.title('System Performance Under Load')
plt.xlabel('Test ID')
plt.ylabel('Response Time (ms)')
plt.legend()
plt.grid(True)

# Save and show the graph
plt.savefig('system_performance.png')
plt.show()
