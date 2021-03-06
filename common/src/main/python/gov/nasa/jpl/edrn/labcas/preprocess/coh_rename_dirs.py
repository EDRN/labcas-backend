# Script to remove blanks from City Of Hope directory names
# 

import os

# parameters
#root_dir = '/Users/cinquini/workbench/labcas/archive/City_Of_Hope'
root_dir = '/labcas-data/City_Of_Hope'

# loop over subdirectories
for thisdir, subdirs, files in os.walk(root_dir):
    
    for subdir in subdirs:
        if ' ' in subdir:
            subdir_path = os.path.join(thisdir, subdir)
            new_subdir_path = subdir_path.replace(' ','')
            print "Renaming %s --> %s" % (subdir_path, new_subdir_path)
            os.rename(subdir_path, new_subdir_path)