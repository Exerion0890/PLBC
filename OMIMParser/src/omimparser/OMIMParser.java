/*
 * To change this license header, choose License Headers reader Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template reader the editor.
 */
package omimparser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.lucene.queryParser.ParseException;

/**
 *
 * @author Fabien
 */
public class OMIMParser {

    private SimpleLuceneSearch lucene;
    private FileInputStream in;
    private FileOutputStream out;
    private int diseaseColNbr, diseaseCount;
    private BufferedReader mim2gene;
    private ConcurrentSkipListSet<String> genes, diseases;

    public static final int GENEMAP1 = 1;
    public static final int GENEMAP2 = 2;

    public OMIMParser(String index, String in, String out, int type) throws Exception {
        lucene = new SimpleLuceneSearch(index);
        this.in = new FileInputStream(in);
        this.out = new FileOutputStream(out);
        genes = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);
        diseases = new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);
        mim2gene = new BufferedReader(new InputStreamReader(new FileInputStream("mim2gene.txt")));
        initGenes(mim2gene);

        switch (type) {
            case GENEMAP1:
                diseaseColNbr = 13;
                diseaseCount = 3;
                break;
            case GENEMAP2:
                diseaseColNbr = 11;
                diseaseCount = 1;
                break;

        }
    }

    /**
     * @param args diseaseColNbrPropertyhe command lreadere
     * argumendiseaseColNbrPropertys
     */
    public static void main(String[] args) {
        // TODO code application logic here
        try {
            OMIMParser parser;
            long tempsDebut = System.currentTimeMillis();
            parser = new OMIMParser(args[0], args[1], args[2], Integer.parseInt(args[3]));
            parser.toRDFFile();
            System.out.println("temps de génération : " + (System.currentTimeMillis() - tempsDebut) + " ms");
        } catch (Exception e) {
            System.err.println(e);
        }

    }

    public String getField(String line, int i) {
        int j = 0, k = 0;
        while (j != i) {
            if (line.charAt(k) == '|') {
                j++;
            }
            k++;
        }
        j = k;
        while (k < line.length() && line.charAt(k) != '|') {
            k++;
        }
        return line.substring(j, k);
    }

    public void toRDFFile() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(this.in));
        OutputStreamWriter writer = new OutputStreamWriter(this.out);
        String line = reader.readLine();
        Triplet diseaseProperty = new Triplet();
        Triplet hasLabel = new Triplet();
        writer.write("@prefix di: <http://telecomnancy.eu/disease/> .\r\n");
        writer.write("@prefix ge: <http://telecomnancy.eu/gene/> .\r\n");
        writer.write("@prefix omim: <http://telecomnancy.eu/omim/> .\r\n");
        writer.write("@prefix rdfs: <http://www.w3c.org/2000/01rdf-schema#> .\r\n");
        String[] data;
        String tmp;
        int noId = 0, total = 0, unfoundId = 0, geneId = 0;
        hasLabel.setProperty("rdfs:hasLabel");
        while (line != null) {
            tmp = getField(line, 5).split(",")[0];
            tmp = GeneSymbolToID(tmp);
            if (tmp == null || tmp.equals("")) {
                geneId++;
                line = reader.readLine();
                continue;
            }
            diseaseProperty.setObject("ge:" + tmp);
            diseaseProperty.setProperty("omim:involvedIn");
            tmp = "";
            for (int i = 0; i < diseaseCount; i++) {
                tmp += getField(line, diseaseColNbr + i);
            }
            data = tmp.split(";");
            String id;
            for (String data1 : data) {
                try {
                    data1 = data1.replaceAll("(.*), ?([0-9]+) ?\\([0-9]+\\)", "$1;$2");
                    if (data1.split(";").length < 2) {
                        noId++;
                        continue;
                    }
                    id = data1.split(";")[1];
                    for (int i = 1; i < id.length(); i++) {
                        if (id.charAt(i) < '0' || id.charAt(i) > '9') {
                            id = id.substring(0, i);
                        }
                    }
                    id = lucene.getCuidFromMimId(id);
                    if (id != null && !id.equals("")) {
                        diseaseProperty.setSubject("di:" + id);
                        writer.write(diseaseProperty + "\r\n");
                        total++;
                        if (diseases.add(id)) {
                            hasLabel.setObject("di:" + id);
                            hasLabel.setSubject("\"" + data1.split(";")[0] + "\"");
                            writer.write(hasLabel + "\r\n");
                        }
                    } else {
                        unfoundId++;
                    }
                } catch (ParseException e) {
                    //err ++;
                }
            }
            line = reader.readLine();
        }
        for (String s : genes) {
            data = s.split(" ");
            hasLabel.setObject("ge:" + data[1]);
            hasLabel.setSubject("\"" + data[0] + "\"");
            writer.write(hasLabel + "\r\n");
        }
        writer.close();
        reader.close();
        System.out.println("\n\n\n\n________________________________________________________");
        System.out.println("diseases without IDs specified : " + noId);
        System.out.println("cuid from omime fails : " + unfoundId);
        System.out.println("gene name to Id fails : " + geneId);
        System.out.println("involvedIn properties generated : " + total);

    }

    private void initGenes(BufferedReader in) throws IOException {
        String line;
        String[] tmpLine;

        line = in.readLine();
        while (line != null) {
            tmpLine = line.split("	");
            if (!tmpLine[3].equals("-")) {
                genes.add(tmpLine[3] + " " + tmpLine[2]);
            }
            line = in.readLine();
        }
        in.close();
    }

    public String GeneSymbolToID(String symbol) throws IOException {
        String gene = genes.tailSet(symbol).first();
        String[] tmp = gene.split(" ");
        if (symbol.equals(tmp[0])) {
            return tmp[1];
        }
        return null;

    }

}
