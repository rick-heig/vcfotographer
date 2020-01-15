package vcfotographer

object TextAssets {
  val logo = """
  |                 IMPRECISE;SVTYPE                  VCFotographer
  |      FILTER   length,assembly=hg19                  
  |    QUAL   BL  PQ                fileDate=2020     Author : Rick Wertenbroek
  |  INFO      1KGP    SVLEN=+42;              FILE     
  |  ID              REF       PASS      VCF     ZU   If you use VCFotographer
  |                BQ   0/1      PASS                 in your research please
  |              SB   ok           PASS               cite :
  |             AA  cG               del
  |            DB  :O                 ins             TODO CITATION
  |            MQ  .                  dup
  |            GL                     inv
  |             MQ                   cnv
  |              ALT                bnd
  |                ook            PASS
  |                  END       PASS
  |  NS                VALIDATED'                GT
  |  GCCACNACGCCTGGCTAATT-----TATTTTTACTAGAGACGGGGT
  |    TATAAAACNANACTTCAGAATTACCATAATATTGATTACAAT
  |
  |
  """.stripMargin
  
  val usage = """Usage: vcfotographer -i variants.vcf -b reads.bam [--scaling factor] [-a additional_track] [--output-dir output_directory]
  |
  |    Additional tracks - Can be of any type supported by IGV, big files may slow IGV down
  """.stripMargin
}