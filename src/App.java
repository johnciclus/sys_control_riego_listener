import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import giovynet.serial.Com;
import giovynet.serial.Baud;
import giovynet.serial.Parameters;
import giovynet.nativelink.SerialPort;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

public class App{
	private static SerialPort serialPort;
	private static List<String> portsFree;
    private static Com com;
    private static Connection con;
    private static Statement stmt;
    private static int codcult;
    
	public static void main(String[] args) throws Exception{ 
        serialPort = new SerialPort();
        portsFree = serialPort.getFreeSerialPort();  
                
        if(portsFree.size() > 0){
        	// Instancia de la conexion COM.
	        com = new Com(paramCOM(portsFree.get(0)));
	        // Instancia de la conexion COM.
	        con = (Connection) paramDB().getConnection();
	        stmt = (Statement) con.createStatement();
	        
	        //Variables para lectura de la trama
	        ResultSet rs;
	        String sql = "";
	        char sc;
	        char[] trama = new char[22]; // Trama de 22 caracteres
	        boolean leer = false;		 // Lectura de trama
	        int tam = 22, ind = 0;
	        int cantSen = 0, cantVal = 0;
	        
	        codcult = 1;
	        
	        sql = "select registro_zigbee.maczigbee from registro_zigbee where codcult = "+codcult+" and tipo = 'sensor'";
	    	rs = stmt.executeQuery(sql);
	        
	    	System.out.println("Sensores activos:");
	    	while(rs.next()){
	    		System.out.println(rs.getString("maczigbee"));
	    		cantSen++;
	    	}
	    	
	    	sql = "select registro_zigbee.maczigbee from registro_zigbee where codcult = "+codcult+" and tipo = 'riego'";
	    	rs = stmt.executeQuery(sql);
	    	
	    	System.out.println("Valvulas de riego activas:");
	    	while(rs.next()){
	    		System.out.println(rs.getString("maczigbee"));
	    		cantVal++;
	    	}
	    	System.out.println();
	    	
	        genTraVal(1);
	        
	        //Lectura del puerto	        
	        while(true){
	        	sc = com.receiveSingleChar();
	        	if(!leer && sc == 0x7E){
	        		leer = true;
	        		System.out.println("Trama: ");
	            }
	        	if(leer){
		        	if(ind<tam){
		        		System.out.print(sc +" ");
		        		trama[ind] = (char)(sc & 0x00FF);
			            ind++;
		        	}
		        	else if(ind == tam){
		        		anaTrama(trama);
		        		leer = false;
		        		ind = 0;
		        		//Thread.sleep(100);
		        	}
	        	}
	        }
	        //con.close();
	        //com.close();
	    }
        else{
        	System.out.println("No se encontro puerto disponible");
        }
    }
    public static Parameters paramCOM(String port) throws Exception{
    	// Definici�n de parametros de la conexion.
    	
    	Parameters settings = new Parameters();
        settings.setPort(port);
        settings.setBaudRate(Baud._9600);
        return settings;
    }
    public static MysqlDataSource paramDB(){
    	MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUser("root");
        dataSource.setPassword("piolin");
        dataSource.setDatabaseName("basesystemteleco");
        dataSource.setServerName("localhost");
        return dataSource;
    }
    public static void anaTrama(char[] trama) throws SQLException{
    	String mac = "", tmp = "", h, sql;
    	float cont = 0;
    	boolean enc = (trama[0] == 0x7E);
    	boolean lon = (trama[1] == 0x00 && trama[2] == 0x12);
    	boolean ide = (trama[3] == 0x92);    	
    	
    	if(enc && lon && ide){
    		System.out.print("Trama:\t\t");
    		for(int i=0; i<trama.length; i++){
	    		h = String.format("%1$02x", (trama[i] & 0xFF));
	    		System.out.print(h+" ");
	    	}
	    	System.out.println();
	    	
	    	System.out.print("MAC:\t\t");
	    	for(int i=4; i<12; i++){
	    		h = String.format("%1$02x", (trama[i] & 0xFF));
	    		mac += h.toUpperCase();
	    	}
	    	System.out.print(mac);
	    	System.out.println();
	    	
	    	System.out.print("Contenido:\t");
	    	for(int i=19; i<21; i++){
	    		h = String.format("%1$02x", (trama[i] & 0xFF));
	    		tmp += h;
	    	}
	    	cont = (float) (0.0833 * (Integer.parseInt(tmp, 16)));
	    	tmp = "";
	    	System.out.print(cont);
	    	System.out.println();

	    	sql = "insert into medicion_variables (idtipovar, maczigbee, varhum, fechamedicion, HoraRegistro)" + 
			 						 "values('1'," +
			 						 "'"+mac+"'," +
			 						 "'"+cont+"',"+
			 						 "current_date,"+
	    							 "current_time)";
	    	
	    	System.out.println(sql);
	    	stmt.execute(sql);
	    	System.out.println();
	    	System.out.println();
    	}
    	else{
    		System.out.println("\nLa trama no se pudo identificar\n");
    	}
    }
    public static void genTraVal(int estado) throws Exception{
    	ResultSet rs;
    	String sql;
    	String mac;
    	String initrama = "";
    	String fintrama = "";
		
		initrama = "7E00101701";
		if(estado == 1){
			fintrama = "FFFE024434050F";
		}
		else if(estado == 0){
			fintrama = "FFFE0244340410";
		}
    	
    	sql = "select registro_zigbee.maczigbee from registro_zigbee where codcult = "+codcult+" and tipo = 'riego'";
    	rs = stmt.executeQuery(sql);
    	
    	if(rs.first()){
    		mac = rs.getString("maczigbee");
    		
    		String tramaStr = initrama+mac+fintrama, sc;
    		char trama[] = new char[20];
    		int hex;
    		
    		System.out.print("inicia envio de trama: ");
    		System.out.println(tramaStr);
    		
    		
    		for(int i=0; i<tramaStr.length()/2; i++){
    			sc = "0x"+tramaStr.substring(i*2, (i*2)+2);
    			hex = Integer.decode(sc);
    			trama[i] = (char) hex;
    		}
    		
    		com.sendArrayChar(trama);
    		
    		sql = "insert into aplicacion_riego (maczigbee, estado, fecha, hora) "+
			  "values('"+mac+"', "+
			  		     estado+", "+
			  		     "current_date, current_time)";
    		stmt.execute(sql);
    		
    		System.out.println("fin de control de valvula de riego");
    	};
    }
}