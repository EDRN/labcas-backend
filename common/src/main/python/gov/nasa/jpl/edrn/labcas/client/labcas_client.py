import xmlrpclib
import time
import solr

class LabcasClient(object):
    '''
    Python client used to interact with a remote Labcas back-end.
    Mostly for demo and example purposes...
    Available methods are defined in Java class org.apache.oodt.cas.workflow.system.XmlRpcWorkflowManager.
    '''
    
    def __init__(self, 
                 workflowManagerUrl='http://localhost:9001/',
                 fileManagerUrl='http://localhost:9000/',
                 solrUrl='http://localhost:8983/solr/oodt-fm',
                 verbose=False):
        
        self.workflowManagerServerProxy = xmlrpclib.ServerProxy(workflowManagerUrl, verbose=verbose)
        self.fileManaferServerProxy = xmlrpclib.ServerProxy(fileManagerUrl, verbose=verbose)
        self.solrServerProxy = solr.SolrConnection(solrUrl)
        
    def getWorkflowsByEvent(self, eventName):
        '''Retrieve a specific workflow by the triggering event.'''
        
        workflows =  self.workflowManagerServerProxy.workflowmgr.getWorkflowsByEvent(eventName)
        for workflow in workflows:
            self.printWorkflow(workflow)
        
    def getWorkflowById(self, workflowId):
        '''Retrieve a specific workflow by its unique identifier.'''
        
        workflow =  self.workflowManagerServerProxy.workflowmgr.getWorkflowById(workflowId)
        self.printWorkflow(workflow)
        
    def executeWorkflow(self, tasks, metadata):
        '''Submits a dynamic workflow composed of the specified tasks, using the specified metadata.'''
        
        # FIXME: pass metadata through: s.encode('ascii',errors='ignore')
        return self.workflowManagerServerProxy.workflowmgr.executeDynamicWorkflow(tasks, metadata)
        
    def waitForCompletion(self, wInstId, debug=False):
        ''' Monitors a workflow instance until it completes.'''
    
        # wait for the server to instantiate this workflow before querying it
        time.sleep(4) 
    
        # now use the workflow instance id to check for status, wait until completed
        running_status  = ['CREATED', 'QUEUED', 'STARTED', 'PAUSED']
        pge_task_status = ['STAGING INPUT', 'BUILDING CONFIG FILE', 'PGE EXEC', 'CRAWLING']
        finished_status = ['FINISHED', 'ERROR', 'METMISS']
        while (True):
            try:
                response = self.workflowManagerServerProxy.workflowmgr.getWorkflowInstanceById(wInstId)
                status = response['status']
                if status in running_status or status in pge_task_status:
                    print 'Workflow instance=%s running with status=%s' % (wInstId, status)
                    time.sleep(1)
                elif status in finished_status:
                    print 'Workflow instance=%s ended with status=%s' % (wInstId, status)
                    break
                else:
                    print 'UNRECOGNIZED WORKFLOW STATUS: %s' % status
                    break
            except xmlrpclib.Fault as e:
                # must ignore XML-RPC exeptions that often arise when querying OODT for a specific workflow
                # just try again with the same workflow identifier
                print e
        if debug:
           print response
        
    def uploadCollection(self, datasetId, metadata, newVersion=False, inPlace=False, debug=False):
        
        # add 'DatasetId' key, value to other metadata
        metadata['DatasetId'] = datasetId
                
        # optionally request a new version
        if newVersion:
            metadata['NewVersion'] = 'true'
    
        # NOTE: currently, if you start a named workflow, the XMLRPC interface only returns True/False, not a workflow instance identifier...
        #tf = serverProxy.workflowmgr.handleEvent('labcas-upload', { 'DatasetId':'mydata' } )
    
        # ... consequently, you must submit an equivalent dynamic workflow, which does return the workflow instance id
        if inPlace:
            wInstId = self.workflowManagerServerProxy.workflowmgr.executeDynamicWorkflow( ['urn:edrn:LabcasUploadInitTask','urn:edrn:LabcasUpload2ExecuteTask'],                                                                           
                                                                                           metadata )
        else:
            wInstId = self.workflowManagerServerProxy.workflowmgr.executeDynamicWorkflow( ['urn:edrn:LabcasUploadInitTask','urn:edrn:LabcasUploadExecuteTask'], 
                                                                                           metadata )
    
        # monitor workflow instance
        self.waitForCompletion(wInstId, debug=debug)
        
    def getProductTypeByName(self, datasetName):
    
        # retrieve a specific product type by name
        productTypeDict =  self.fileManaferServerProxy.filemgr.getProductTypeByName(datasetName)
        
        self.printProductType(productTypeDict)
    
    def getProductTypeById(self, datasetId):
        
        # retrieve a specific product type by name
        productTypeDict =  self.fileManaferServerProxy.filemgr.getProductTypeById(datasetId)
        
        self.printProductType(productTypeDict)
        
    def listProductTypes(self):
        
        # list all supported product types
        productTypes =  self.fileManaferServerProxy.filemgr.getProductTypes()
        for productTypeDict in productTypes:
            self.printProductType(productTypeDict)
            
    def listTopLevelProductTypes(self):
        
        # roots of product type hierarchy (NOT be displayed directly)
        BUILTIN_PRODUCTS = ['GenericFile', 'LabcasProduct','EcasProduct']
        
        # dictionary of top level product types:
        # key = DatasetId, value = dictionary of properties
        topLevelProductTypes = {}
        
        # list all supported product types
        productTypes =  self.fileManaferServerProxy.filemgr.getProductTypes()
        for productTypeDict in productTypes:

            # assemble information to be displayed by UI
            name = productTypeDict['name']
            
            # do NOT include these types in display list
            if name not in BUILTIN_PRODUCTS:
                
                typeMetadata = productTypeDict['typeMetadata']
                datasetId = typeMetadata['DatasetId'][0]
                try:
                    datasetName = typeMetadata['DatasetName'][0]
                except KeyError:
                    datasetName = None
                
                description = productTypeDict['description']
                if typeMetadata.get('OrganSite', None):
                    organSite = typeMetadata['OrganSite'][0]
                else:
                    organSite = None
                if typeMetadata.get('LeadPI', None):
                    leadPI = typeMetadata['LeadPI'][0]
                else:
                    leadPI = None
                
                topLevelProductTypes[datasetId] = { 'name': name,
                                                    'description':  description,
                                                    'datasetId': datasetId,
                                                    'datasetName': datasetName,
                                                    'organSite': organSite,
                                                    'leadPI':leadPI }
                
                
        return topLevelProductTypes
    
    def listProductTypesByParent(self, parentDatasetId):
        
        childrenProductTypes = []
        
        # list all supported product types
        productTypes =  self.fileManaferServerProxy.filemgr.getProductTypes()
        for productTypeDict in productTypes:
            try:
                if parentDatasetId in productTypeDict['typeMetadata']['ParentDatasetId']:
                    datasetId =  productTypeDict['typeMetadata']['DatasetId'][0]
                    childrenProductTypes.append(datasetId)
            except KeyError:
                pass
                    
        return childrenProductTypes
        
    
    def printProductType(self, productTypeDict):
        print 'PRODUCT TYPE: %s' % productTypeDict['name']
        for key, value in productTypeDict.items():
            # dictionary: typeMetadata = {'DataCustodianEmail': ['dsidrans@jhmi.edu'], 'DataDisclaimer': [...], ..}
            if key=='typeMetadata':
                print '\t%s =' % key
                for _key, _value in value.items():
                    print '\t\t%s = %s' % (_key, _value)
            else:
                print '\t%s = %s' % (key, value)
    
    def listProducts(self, productType):
        
        # query for all products of this type (i.e. all files of this dataset), all versions
        #response = self.solrServerProxy.query('*:*', fq=['DatasetId:%s' % datasetId], start=0)
        #print "\nNumber of files found: %s (all versions)" % response.numFound
        #for result in response.results:
        #    self.printProduct(result)
            
        # query for all possible versions of this dataset
        response = self.solrServerProxy.query('*:*', fq=['CAS.ProductTypeName:%s' % productType], start=0, rows=0, facet='true', facet_field='DatasetVersion')
        versions = response.facet_counts['facet_fields']['DatasetVersion']
        last_version = 0
        for key, value in versions.items():
            # NOTE: facet keys span the whole index, but their counts are specific to this search
            if int(value)>0:
                print "\nDataset Version number %s has %s files" % (key, value)
                if int(key) > last_version:
                    last_version = int(key)
            
        # query for all files for a specific version
        response = self.solrServerProxy.query('*:*', fq=['CAS.ProductTypeName:%s' % productType,'DatasetVersion:%s' % last_version ], start=0)
        print "\nLatest version: %s, number of files: %s, listing them all:" % (last_version, response.numFound)
        for result in response.results:
            self.printProduct(result)
        
    def printProduct(self, result):
        '''Utility function to print out the product metadata'''
        
        print '\nProduct ID=%s' % result['id']
        for key, values in result.items():
            print '\tProduct metadata key=%s values=%s' % (key, values)

    def printWorkflow(self, workflowDict):
        '''Utiliyu function to print out a workflow.'''
        
        print workflowDict
        print "Workflow id=%s name=%s" % (workflowDict['id'], workflowDict['name'])
        for task in workflowDict['tasks']:
            print "Task: %s" % task