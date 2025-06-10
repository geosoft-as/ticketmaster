#
# Script that can be used for updaing commit history of a Git repository
# in order to substitute old ticket numbers with new ones.
#
# Use with like this:
#   python ../git-filter-repo --force --message-callback 'from updateGit import replace_issue_number; return replace_issue_number(message.decode("utf-8")).encode("utf-8")'
#
# mapping.json contains the mapping in the following format:
#
# {
#    "<old id_1>": "<new id_1>",
#    "<old id_2>": "<new id_2>",
#    :
#    "<old id_1>": "<new id_1>"
# }
#
# The JSON array can be created by MappingTool.java.
#
import json
import re

script_dir = os.path.dirname(os.path.abspath(__file__))
mapping_path = os.path.join(script_dir, 'mapping.json')

with open(mapping_path, 'r') as file:
  mapping = json.load(file)

# Pattern to match Jira issue keys (e.g., SK-123)
jira_key_pattern = re.compile(r'\b([A-Za-z]+-\d+)\b')

def replace_issue_number(text):
  matches = jira_key_pattern.findall(text)
  for key in matches:
    if key in mapping:
      text = text.replace(key, f"AB#{mapping[key]}")

  return text


# Test input
example_text = "SK-123 this fixes a bug RnD-23"
updated_text = replace_issue_number(example_text)

# Print the result
print("Original text: ", example_text)
print("Updated text:  ", updated_text)
