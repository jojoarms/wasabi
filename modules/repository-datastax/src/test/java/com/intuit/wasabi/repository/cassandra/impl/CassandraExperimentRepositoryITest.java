package com.intuit.wasabi.repository.cassandra.impl;

import com.datastax.driver.core.Session;
import com.datastax.driver.mapping.MappingManager;
import com.datastax.driver.mapping.Result;
import com.google.common.collect.Table;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.intuit.wasabi.analyticsobjects.counts.AssignmentCounts;
import com.intuit.wasabi.cassandra.datastax.CassandraDriver;
import com.intuit.wasabi.exceptions.ConstraintViolationException;
import com.intuit.wasabi.experimentobjects.Application;
import com.intuit.wasabi.experimentobjects.Application.Name;
import com.intuit.wasabi.experimentobjects.Bucket;
import com.intuit.wasabi.experimentobjects.BucketList;
import com.intuit.wasabi.experimentobjects.Context;
import com.intuit.wasabi.experimentobjects.Experiment.Label;
import com.intuit.wasabi.experimentobjects.ExperimentList;
import com.intuit.wasabi.experimentobjects.ExperimentValidator;
import com.intuit.wasabi.experimentobjects.Bucket.State;
import com.intuit.wasabi.experimentobjects.Experiment;
import com.intuit.wasabi.experimentobjects.Experiment.ID;
import com.intuit.wasabi.experimentobjects.NewExperiment;
import com.intuit.wasabi.repository.AuditLogRepository;
import com.intuit.wasabi.repository.RepositoryException;
import com.intuit.wasabi.repository.cassandra.CassandraRepositoryModule;
import com.intuit.wasabi.repository.cassandra.accessor.ApplicationListAccessor;
import com.intuit.wasabi.repository.cassandra.accessor.BucketAccessor;
import com.intuit.wasabi.repository.cassandra.accessor.ExperimentAccessor;
import com.intuit.wasabi.repository.cassandra.accessor.audit.BucketAuditLogAccessor;
import com.intuit.wasabi.repository.cassandra.accessor.audit.ExperimentAuditLogAccessor;
import com.intuit.wasabi.repository.cassandra.accessor.index.ExperimentLabelIndexAccessor;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CassandraExperimentRepositoryITest {

    ExperimentAccessor experimentAccessor;

    CassandraExperimentRepository repository;

	private Session session;

	private MappingManager manager;

	private String applicationName;

	private CassandraDriver driver;

	private ID experimentID1;
	private ID experimentID2;

	private Bucket bucket1;
	private Bucket bucket2;

	private NewExperiment newExperiment1;
	private NewExperiment newExperiment2;

	private ExperimentValidator validator;

	private ExperimentLabelIndexAccessor experimentLabelIndexAccessor;
	private ApplicationListAccessor applicationListAccessor;

	private BucketAccessor bucketAccessor;

	private Context QA = Context.valueOf("qa");
	private Application.Name appname;

	private BucketAuditLogAccessor bucketAuditLogAccessor;
	private ExperimentAuditLogAccessor experimentAuditLogAccessor;
	
    @Before
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new CassandraRepositoryModule());
        injector.getInstance(Key.get(String.class, Names.named("CassandraInstanceName")));
       
        session = injector.getInstance(CassandraDriver.class).getSession();
        driver = injector.getInstance(CassandraDriver.class);
        manager = new MappingManager(session);
        
        appname = Application.Name.valueOf("app1" + System.currentTimeMillis());
        
        experimentAccessor = injector.getInstance(ExperimentAccessor.class);
        bucketAuditLogAccessor = injector.getInstance(BucketAuditLogAccessor.class);
        experimentAuditLogAccessor = injector.getInstance(ExperimentAuditLogAccessor.class);
        bucketAccessor = injector.getInstance(BucketAccessor.class);
        experimentLabelIndexAccessor = injector.getInstance(
        		ExperimentLabelIndexAccessor.class);
        
        applicationListAccessor = injector.getInstance(ApplicationListAccessor.class);
        
        applicationName = "ApplicationName_" + System.currentTimeMillis();
        session.execute("truncate wasabi_experiments.bucket");
        
        validator = new ExperimentValidator(); 
        
        session.execute("delete from wasabi_experiments.auditlog where application_name = '" 
        		+ AuditLogRepository.GLOBAL_ENTRY_APPLICATION.toString() + "'");
        
		experimentID1 = Experiment.ID.valueOf(UUID.randomUUID());
		experimentID2 = Experiment.ID.valueOf(UUID.randomUUID());
		
    	repository = injector.getInstance(CassandraExperimentRepository.class);;
    	bucket1 = Bucket.newInstance(experimentID1,Bucket.Label.valueOf("bl1")).withAllocationPercent(.23)
    			.withControl(true)
    			.withDescription("b1").withPayload("p1")
    			.withState(State.OPEN).build();

    	bucket2 = Bucket.newInstance(experimentID2,Bucket.Label.valueOf("bl2"))
    			.withAllocationPercent(.24).withControl(false)
    			.withDescription("b2").withPayload("p2")
    			.withState(State.OPEN).build();
    	
    	newExperiment1 = new NewExperiment(experimentID1);
    	newExperiment1.setApplicationName(appname);
    	newExperiment1.setLabel(Experiment.Label.valueOf("el1" + System.currentTimeMillis()));
    	newExperiment1.setCreatorID("c1");
    	newExperiment1.setDescription("ed1");
    	newExperiment1.setStartTime(new Date());
    	newExperiment1.setEndTime(new Date());
    	newExperiment1.setSamplingPercent(0.2);
    	experimentID1 = repository.createExperiment(newExperiment1);
    	
    	repository.createIndicesForNewExperiment(newExperiment1);

    	newExperiment2 = new NewExperiment(experimentID2);
    	newExperiment2.setApplicationName(appname);
    	newExperiment2.setLabel(Experiment.Label.valueOf("el2" + System.currentTimeMillis()));
    	newExperiment2.setCreatorID("c2");
    	newExperiment2.setDescription("ed2");
    	newExperiment2.setStartTime(new Date());
    	newExperiment2.setEndTime(new Date());
    	newExperiment2.setSamplingPercent(0.2);
    	experimentID2 = repository.createExperiment(newExperiment2);
    	
    	repository.createIndicesForNewExperiment(newExperiment2);
}
    
	@Test
	public void testCreateBatchBucketWith2BucketsSuccess() {
		BucketList bucketList = new BucketList();
		bucketList.addBucket(bucket1);
		bucketList.addBucket(bucket2);
		
		repository.updateBucketBatch(experimentID1, bucketList);
		
		BucketList result = repository.getBucketList(experimentID1);
		assertEquals("Value should be equal", 2, result.getBuckets().size());
	}

	@Test
	public void testGetExperimentByIdSuccess() {
		Experiment experiment = repository.getExperiment(experimentID1);
		
		assertEquals("Value should be equal", experimentID1, experiment.getID());
	}

	@Test
	public void testGetExperimentByAppSuccess() {
		Table<ID, Label, Experiment> experiments = 
				repository.getExperimentList(newExperiment1.getApplicationName());
		
		assertEquals("Value should be equal", 2, experiments.size());
	}

	@Test
	public void testGetExperimentsByAppWithOneTerminatedSuccess() {
		Table<ID, Label, Experiment> experiments = 
				repository.getExperimentList(appname);
		
		assertEquals("Value should be equal", 2, experiments.size());
		
		Experiment experiment1 = repository.updateExperimentState(experiments.get(experimentID1, newExperiment1.getLabel()),
				Experiment.State.TERMINATED);
		
		assertEquals("Values should be eq", Experiment.State.TERMINATED, experiment1.getState());
		
		experiments = repository.getExperimentList(appname);
		
		assertEquals("Value should be equal", 1, experiments.size());
	    assertEquals("Value should be equal", experimentID2, experiments.get(experimentID2, newExperiment2.getLabel()).getID());
	}

	@Test
	public void testGetExperimentListByAppWithOneTerminatedSuccess() {
		Table<ID, Label, Experiment> experiments = 
				repository.getExperimentList(appname);
		
		assertEquals("Value should be equal", 2, experiments.size());
		
		Experiment experiment1 = repository.updateExperimentState(experiments.get(experimentID1, newExperiment1.getLabel()),
				Experiment.State.TERMINATED);
		
		assertEquals("Values should be eq", Experiment.State.TERMINATED, experiment1.getState());
		
		experiments = repository.getExperimentList(appname);
		
		assertEquals("Value should be equal", 1, experiments.size());
						
	}

	@Test
	public void testCreateAppGetApplicationSuccess() {
		int size = repository.getApplicationsList().size();
		Name app = Application.Name.valueOf("appx" + System.currentTimeMillis());
		repository.createApplication(app);
		
		List<Name> apps = repository.getApplicationsList();
		assertEquals("Value should be equal", size + 1, apps.size());
		assertTrue("App should be in the list " + apps, 
				apps.stream().anyMatch(application -> application.toString().equals(app.toString())));
	}

	@Test(expected=NullPointerException.class)
	public void testGetExperimentNullThrowsException() {
		Experiment experiment = repository.getExperiment(null);		
	}

	@Test(expected=RepositoryException.class)
	public void testGetExperimentAccessorNullThrowsException() {
		repository.setExperimentAccessor(null);
		Experiment experiment = repository.getExperiment(experimentID1);		
	}

	@Test
	public void testGetExperimentsSuccess() {
		List<ID> experimentIds1 = repository.getExperiments();		
		
		newExperiment2.setLabel(Experiment.Label.valueOf("lbl" + System.currentTimeMillis()));
		newExperiment2.setId(Experiment.ID.newInstance());
		repository.createExperiment(newExperiment2);
		repository.createIndicesForNewExperiment(newExperiment2);
		List<ID> experimentIds2 = repository.getExperiments();		
		
		assertEquals("Values should be eq", experimentIds1.size() + 1, experimentIds2.size() );
		assertTrue("Id " + newExperiment2.getID() + " should in list " + 
		experimentIds2, experimentIds2.contains(newExperiment2.getId()));
	}

	@Test(expected=RepositoryException.class)
	public void testGetExperimentLabelIndexAccessorNullThrowsException() {
		repository.setExperimentLabelIndexAccessor(null);
		Experiment experiment = repository.getExperiment(appname, newExperiment1.getLabel());		
	}

	@Test
	public void testGetExperimentByAppAndLabelSuccess() {
		Experiment experiment = repository.getExperiment(newExperiment1.getApplicationName(),
				newExperiment1.getLabel());
		assertEquals("Value should be equal", experimentID1, experiment.getID());
	}

	@Test
	public void testGetExperimentBucketListNoBucketsByIds() {
		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		experimentIds.add(experimentID2);
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 0, buckets.size());
	}

	@Test
	public void testGetExperimentsByTwoIds() {
		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		experimentIds.add(experimentID2);
		 ExperimentList experiments = repository.getExperiments(experimentIds);
		assertEquals("Value should be equal", 2, experiments.getExperiments().size());
	}

	@Test
	public void testGetExperimentsByOneId() {
		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		 ExperimentList experiments = repository.getExperiments(experimentIds);
		assertEquals("Value should be equal", 1, experiments.getExperiments().size());
		assertEquals("Value should be equal", experimentID1, experiments.
				getExperiments().get(0).getID());
	}

	@Test(expected=RepositoryException.class)
	public void testGetExperimentsStateExperimentAccesorNullSuccess() {
		repository.setStateExperimentIndexAccessor(null);
		repository.getExperiments();
	}


	@Test
	public void testGetOneBucketListEachByIds() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		
		repository.updateBucketBatch(experimentID1, bucketList1);

		BucketList bucketList2 = new BucketList();
		bucketList2.addBucket(bucket2);
		
		repository.updateBucketBatch(experimentID2, bucketList2);

		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		experimentIds.add(experimentID2);
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 2, buckets.size());
		assertEquals("Value should be equal", 1, buckets.get(experimentID1).getBuckets().size());
		assertEquals("Value should be equal", bucket1, buckets.get(experimentID1).getBuckets().get(0));
		assertEquals("Value should be equal", 1, buckets.get(experimentID2).getBuckets().size());
		assertEquals("Value should be equal", bucket2, buckets.get(experimentID2).getBuckets().get(0));

	}

	@Test(expected=RepositoryException.class)
	public void testGetOneBucketListBucketAccessorNull() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		
		repository.updateBucketBatch(experimentID1, bucketList1);

		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		experimentIds.add(experimentID2);
		
		repository.setBucketAccessor(null);
		
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
	}

	@Test
	public void testGetOneBucketsAndDeleteBucket() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		
		repository.updateBucketBatch(experimentID1, bucketList1);

		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 1, buckets.size());
		assertEquals("Value should be equal", 1, buckets.get(experimentID1).getBuckets().size());
		assertEquals("Value should be equal", bucket1, buckets.get(experimentID1).getBuckets().get(0));

		repository.deleteBucket(experimentID1, bucket1.getLabel());
		
		buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 0, buckets.size());
		
	}

	@Test
	public void testUpdateBucketAllocation() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		
		repository.updateBucketBatch(experimentID1, bucketList1);

		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 1, buckets.size());
		assertEquals("Value should be equal", 1, buckets.get(experimentID1).getBuckets().size());
		assertEquals("Value should be equal", bucket1, buckets.get(experimentID1).getBuckets().get(0));
		
		assertEquals("Value should be eq", bucket1.getAllocationPercent(),buckets.get(experimentID1).getBuckets().get(0).getAllocationPercent() );
		
		Bucket bucketResult = repository.updateBucketAllocationPercentage(bucket1, 0.75);
		assertEquals("Value should be eq", experimentID1, bucketResult.getExperimentID());
		assertEquals("Value should be eq", 0.75, bucketResult.getAllocationPercent(), 0.001);
		
	}

	@Test
	public void testUpdateBucket() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		
		repository.updateBucketBatch(experimentID1, bucketList1);

		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 1, buckets.size());
		assertEquals("Value should be equal", 1, buckets.get(experimentID1).getBuckets().size());
		assertEquals("Value should be equal", bucket1, buckets.get(experimentID1).getBuckets().get(0));
		
		assertEquals("Value should be eq", bucket1.getAllocationPercent(),buckets.get(experimentID1).getBuckets().get(0).getAllocationPercent() );
		
		String description = "newDescription" + System.currentTimeMillis();
		bucket1.setDescription(description);
		Bucket bucketResult = repository.updateBucket(bucket1);

		assertEquals("Value should be eq", experimentID1, bucketResult.getExperimentID());
		assertEquals("Value should be eq", bucket1.getAllocationPercent(), bucketResult.getAllocationPercent(), 0.001);
		assertEquals("Value should be eq", description, bucketResult.getDescription());
		
	}

	@Test
	public void testUpdateExperiment() {

		Experiment experiment = repository.getExperiment(experimentID1);
		String description = "newDescription" + System.currentTimeMillis();
		experiment.setDescription(description);
		repository.updateExperiment(experiment);
		
		experiment = repository.getExperiment(experimentID1);
		assertEquals("Value should be eq", experimentID1, experiment.getID());
		assertEquals("Value should be eq", description, experiment.getDescription());
	}

	@Test
	public void testUpdateExperimentState() {

		Experiment experiment = repository.getExperiment(experimentID1);
		assertEquals("Value should be eq", Experiment.State.DRAFT, experiment.getState());

		repository.updateExperimentState(experiment, Experiment.State.RUNNING);
		
		experiment = repository.getExperiment(experimentID1);
		assertEquals("Value should be eq", experimentID1, experiment.getID());
		assertEquals("Value should be eq", Experiment.State.RUNNING, experiment.getState());
	}

	@Test
	public void testUpdateBucketState() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		
		repository.updateBucketBatch(experimentID1, bucketList1);

		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 1, buckets.size());
		assertEquals("Value should be equal", 1, buckets.get(experimentID1).getBuckets().size());
		assertEquals("Value should be equal", Bucket.State.OPEN, buckets.get(experimentID1).getBuckets().get(0).getState());

		Bucket resultBucket = repository.updateBucketState(bucket1, Bucket.State.CLOSED);
		assertEquals("Value should be eq", Bucket.State.CLOSED,resultBucket.getState());
		
	}

	@Test
	public void testGetTwoBucketsEachByIds() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		bucketList1.addBucket(bucket2);
		repository.updateBucketBatch(experimentID1, bucketList1);

		List<Experiment.ID> experimentIds = new ArrayList<>();
		experimentIds.add(experimentID1);
		Map<ID, BucketList> buckets = repository.getBucketList(experimentIds);
		assertEquals("Value should be equal", 1, buckets.size());
		assertEquals("Value should be equal", 2, buckets.get(experimentID1).getBuckets().size());
		BucketList bkts = buckets.get(experimentID1);
		List<Bucket> bucketsResponse = bkts.getBuckets();
		
		Collections.sort(bucketsResponse, new Comparator<Bucket>() {
			@Override
			public int compare(Bucket o1, Bucket o2) {
				return o1.getLabel().toString().compareTo(o2.getLabel().toString());
			}
		});
		
		List<Bucket> bucketsExpected = bucketList1.getBuckets();
		
		Collections.sort(bucketsExpected, new Comparator<Bucket>() {
			@Override
			public int compare(Bucket o1, Bucket o2) {
				return o1.getLabel().toString().compareTo(o2.getLabel().toString());
			}
		});
		
		assertTrue("lists should be eq", bucketsExpected.get(0).getLabel()
				.equals(bucketsResponse.get(0).getLabel()));
		assertTrue("lists should be eq", bucketsExpected.get(1).getLabel()
				.equals(bucketsResponse.get(1).getLabel()));
	}

	@Test(expected=NullPointerException.class)
	public void testGetExperimentByAppNullThrowsException() {
		Experiment experiment = repository.getExperiment(null,newExperiment1.getLabel());
	}
	
	@Test(expected=NullPointerException.class)
	public void testGetExperimentByLabelNullThrowsException() {
		Experiment experiment = repository.getExperiment(newExperiment1.getApplicationName(),null);
		
	}

	@Test
	public void testGetAssigmentsCountWithNoAssignmentSuccess() {
		BucketList bucketList = new BucketList();
		bucketList.addBucket(bucket1);
		repository.updateBucketBatch(experimentID1, bucketList);

		AssignmentCounts count = repository.getAssignmentCounts(experimentID1, QA);
		assertEquals("Value should be eq", 2, count.getAssignments().size());
		assertEquals("Value should be eq", bucket1.getLabel(), 
				count.getAssignments().get(0).getBucket());
		assertEquals("Value should be eq", newExperiment1.getId(), 
				count.getExperimentID());
		assertEquals("Value should be eq", 0, 
				count.getAssignments().get(0).getCount());
		
		assertEquals("Value should be eq", null, 
				count.getAssignments().get(1).getBucket());
		assertEquals("Value should be eq", 0, 
				count.getAssignments().get(1).getCount());
	}
	
	@Test
	public void testLogBucketAuditSuccess() {
		String bucketLabel = "bkt" + System.currentTimeMillis();
		Result<?> logs = 
				bucketAuditLogAccessor.selectBy(experimentID1.getRawID(), 
						bucketLabel);
	
		assertEquals("Size should be same", 0, logs.all().size());
		
		List<Bucket.BucketAuditInfo> auditLog = new ArrayList<>();
		Bucket.BucketAuditInfo log = new Bucket.BucketAuditInfo("attr1", "o1", "n1");
		auditLog.add(log);
		
		repository.logBucketChanges(experimentID1, Bucket.Label.valueOf(bucketLabel), auditLog );

		logs = 
				bucketAuditLogAccessor.selectBy(experimentID1.getRawID(), 
						bucketLabel);
		
		assertEquals("Size should be same", 1, logs.all().size());

	}

	@Test
	public void testLogExperimentAuditSuccess() {
		Experiment.ID expid = Experiment.ID.newInstance();
		
		Result<?> logs = 
				experimentAuditLogAccessor.selectBy(expid.getRawID());
	
		assertEquals("Size should be same", 0, logs.all().size());
		
		List<Experiment.ExperimentAuditInfo> auditLog = new ArrayList<>();
		Experiment.ExperimentAuditInfo log = new Experiment.ExperimentAuditInfo("attr1", "o1", "n1");
		auditLog.add(log);
		
		repository.logExperimentChanges(expid, auditLog);
		logs = 
				experimentAuditLogAccessor.selectBy(expid.getRawID());
		
		assertEquals("Size should be same", 1, logs.all().size());

	}

	// NOTE - IMHO - The createIndicesForNewExperiment should not be in the interface  
	@Test(expected=RepositoryException.class)
	public void testCreateIndexesForExperimentStateIndexAccessorNullThrowsException() {
		newExperiment1.setId(Experiment.ID.newInstance());
		repository.setStateExperimentIndexAccessor(null);
		repository.createIndicesForNewExperiment(newExperiment1);		
	}

	
	@Test
	public void testGetBucketsByExperimentId() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		bucketList1.addBucket(bucket2);
		repository.updateBucketBatch(experimentID1, bucketList1);

		 BucketList buckets = repository.getBuckets(experimentID1);
		assertEquals("Value should be equal", 2, buckets.getBuckets().size());
		List<Bucket> bucketsResponse = buckets.getBuckets();
		
		Collections.sort(bucketsResponse, new Comparator<Bucket>() {
			@Override
			public int compare(Bucket o1, Bucket o2) {
				return o1.getLabel().toString().compareTo(o2.getLabel().toString());
			}
		});
		
		List<Bucket> bucketsExpected = bucketList1.getBuckets();
		
		Collections.sort(bucketsExpected, new Comparator<Bucket>() {
			@Override
			public int compare(Bucket o1, Bucket o2) {
				return o1.getLabel().toString().compareTo(o2.getLabel().toString());
			}
		});
		
		assertTrue("lists should be eq", bucketsExpected.get(0).getLabel()
				.equals(bucketsResponse.get(0).getLabel()));
		assertTrue("lists should be eq", bucketsExpected.get(1).getLabel()
				.equals(bucketsResponse.get(1).getLabel()));
	}

	@Test
	public void testCreateTwoBucketsAndDeleteOneBucket() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		bucketList1.addBucket(bucket2);
		repository.updateBucketBatch(experimentID1, bucketList1);

		BucketList buckets = repository.getBuckets(experimentID1);
		assertEquals("Value should be equal", 2, buckets.getBuckets().size());

		repository.deleteBucket(experimentID1, bucket1.getLabel());
		
		buckets = repository.getBuckets(experimentID1);
		
		assertEquals("Value should be equal", 1, buckets.getBuckets().size());
		List<Bucket> bucketsResponse = buckets.getBuckets();
		
		List<Bucket> bucketsExpected = bucketList1.getBuckets();
		
		assertTrue("lists should be eq", bucketsExpected.get(1).getLabel()
				.equals(bucketsResponse.get(0).getLabel()));
	}

	@Test(expected=RepositoryException.class)
	public void testCreateExperimentAccessorNullThrowsException() {
		newExperiment1.setId(Experiment.ID.newInstance());
		newExperiment1.setLabel(Experiment.Label.valueOf("lbl" + System.currentTimeMillis()));
		repository.setExperimentAccessor(null);
		ID experimentId = repository.createExperiment(newExperiment1);		
	}

	@Test
	public void testCreateOneBucketsAndDeleteOneBucket() {
		BucketList bucketList1 = new BucketList();
		bucketList1.addBucket(bucket1);
		repository.updateBucketBatch(experimentID1, bucketList1);

		BucketList buckets = repository.getBuckets(experimentID1);
		assertEquals("Value should be equal", 1, buckets.getBuckets().size());

		repository.deleteBucket(experimentID1, bucket1.getLabel());
		
		buckets = repository.getBuckets(experimentID1);
		
		assertEquals("Value should be equal", 0, buckets.getBuckets().size());
		
	}

	@Test
	public void testGetterSetter() {
		assertNotNull("value should be eq",  repository.getApplicationListAccessor());
		assertNotNull("value should be eq",  repository.getBucketAuditLogAccessor());
		assertNotNull("value should be eq",  repository.getExperimentAccessor());
		assertNotNull("value should be eq",  repository.getExperimentAuditLogAccessor());
		assertNotNull("value should be eq",  repository.getStateExperimentIndexAccessor());
		assertNotNull("value should be eq",  repository.getExperimentAccessor());
		assertNotNull("value should be eq",  repository.getBucketAccessor());
		assertNotNull("value should be eq",  repository.getExperimentLabelIndexAccessor());
		assertNotNull("value should be eq",  repository.getUserBucketIndexAccessor());
		
		repository.setApplicationListAccessor(null);;
		assertEquals("Value should be eq", null, repository.getApplicationListAccessor());
		
		repository.setBucketAccessor(null);
		assertEquals("Value should be eq", null, repository.getBucketAccessor());

		repository.setExperimentAccessor(null);
		assertEquals("Value should be eq", null, repository.getExperimentAccessor());
		
		repository.setBucketAuditLogAccessor(null);
		assertEquals("Value should be eq", null, repository.getBucketAuditLogAccessor());

		repository.setExperimentAuditLogAccessor(null);
		assertEquals("Value should be eq", null, repository.getExperimentAuditLogAccessor());

		repository.setStateExperimentIndexAccessor(null);
		assertEquals("Value should be eq", null, repository.getStateExperimentIndexAccessor());

		repository.setUserBucketIndexAccessor(null);
		assertEquals("Value should be eq", null, repository.getUserBucketIndexAccessor());

		repository.setExperimentLabelIndexAccessor(null);
		assertEquals("Value should be eq", null, repository.getExperimentLabelIndexAccessor());
	}
	
	@Test(expected=RepositoryException.class)
	public void testCreateExperimentExperimentAccessorNullThrowsException() {
		repository.setExperimentAccessor(null);
		newExperiment1.setId(Experiment.ID.newInstance());;
		ID experimentId = repository.createExperiment(newExperiment1);		
	}

	@Test(expected=ConstraintViolationException.class)
	public void testCreateExperimentDuplicateThrowsException() {
		ID experimentId = repository.createExperiment(newExperiment1);		
	}
}