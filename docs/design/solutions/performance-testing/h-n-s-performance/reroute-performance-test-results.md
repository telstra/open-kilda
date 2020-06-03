# H&S reroute - performance test results

Here's the result of load testing for reroute operation with simultaneous requests (from 1 to 100). This was executed
on test environment with the following configuration:
- 100 virtual switches in a mesh topology.
- 300 flows.
- 64 as parallelizm for H&S Storm topology. 


|Requests|Total<br>execution<br>time|NB-> Service|FSM-Execution|Resource<br>Allocation|PCE   |InstallCmd<br>Roundtrip|Cmd-OutTrans|Cmd-InTrans|ValidateCmd<br>Roundtrip|Cmd-OutTrans|Cmd-InTrans|Swap Paths|FSM-CompleteInstall  |FSM-CompleteRemove	|FSM-Deallocation	|FSM-FlowStatus |
|--------|--------------------------|------------|-------------|----------------------|------|-----------------------|------------|-----------|------------------------|------------|-----------|----------|---------------------|-------------------|-------------------|---------------|
|1		 |2		                    |0.08	 	 |1.24		  |0.35	                 |0.3	|0.14			        |0.07		 |0.07		 |0.17				      |0.07		   |0.1		   |0.03	  |0.01					|0.06				|0.02			    |0.01           |
|8		 |4+2	                    |0.15		 |3.85		  |2.1	                 |1.1	|0.9			        |0.18		 |0.8		 |0.35				      |0.15		   |0.16	   |0.03	  |0.01					|0.18				|0.02			    |0.01			|
|16		 |4+6	                    |0.2		 |4.1	      |2.4	                 |1.2	|0.5			        |0.2		 |0.2		 |0.7				      |0.15		   |0.5		   |0.03	  |0.01					|0.3				|0.02			    |0.01			|
|32		 |7+18	                    |0.7		 |7.3		  |2.9	                 |1.7	|0.7			        |0.25		 |0.5		 |1.25			   	      |0.2		   |1		   |0.04	  |0.02					|0.4				|0.02		    	|0.01           |
|48		 |9+22	                    |0.7		 |8.5		  |3.2	                 |2.2	|0.95			        |0.25		 |0.7		 |1.5				      |0.25		   |1.25	   |0.1	      |0.02					|0.4				|0.02		    	|0.01           |
|64		 |9+30	                    |0.7		 |8.5		  |3.4	                 |2.3	|1.9			        |0.27		 |1.6		 |2.3				      |0.25		   |2.05	   |0.1	      |0.02					|0.4				|0.02		    	|0.01           |
|80		 |10+50	                    |3(4.5 max)	 |9.5		  |4.5	                 |3.25	|3.5			        |1.1		 |2.5		 |2.5				      |0.35		   |2.1		   |0.15	  |0.05					|0.7				|0.05		    	|0.08           |
|100	 |15+70	                    |5(13 max)	 |15			  |8	                 |5		|5.5			        |1.1		 |4.5(7 max) |6.5				      |1.1		   |5.5		   |0.15	  |0.05					|0.9				|0.05		    	|0.08           |

*Note: all time values are in seconds.* 










