package com.somsakelect.android.candemo;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.cpdevice.cpcomm.boards.CPDEVICE;
import com.cpdevice.cpcomm.common.CPCanFrameRxListener;
import com.cpdevice.cpcomm.common.SocketCanFrameRxListener;
import com.cpdevice.cpcomm.common.ValueChangedListener;
import com.cpdevice.cpcomm.datalink.CPV1DataLink;
import com.cpdevice.cpcomm.datalink.CPV3DataLink;
import com.cpdevice.cpcomm.datalink.DataLink;
import com.cpdevice.cpcomm.datalink.DefaultDataLink;
import com.cpdevice.cpcomm.exception.CPBusException;
import com.cpdevice.cpcomm.frame.ICPCanFrame;
import com.cpdevice.cpcomm.port.Port;
import com.cpdevice.cpcomm.port.SocketCan;
import com.cpdevice.cpcomm.port.SpiPort;
import com.cpdevice.cpcomm.port.Uart;
import com.cpdevice.cpcomm.proto.CPV1Protocol;
import com.cpdevice.cpcomm.proto.CPV3Protocol;
import com.cpdevice.cpcomm.proto.Protocol;
import com.cpdevice.cpcomm.proto.SocketCanProtocol;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    SpiPort      mSpi0;           //port
    CPV1DataLink cpv1DataLink;  //datalink
    CPV1Protocol cpv1Protocol;  //protocol

    SocketCan mSocketCan0;
    DefaultDataLink mDataLink0;
    SocketCanProtocol mSocketCanProtocol0;
    byte[] mCanPack = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
    int mCanId0 = 0x110;//扩展帧

    SocketCan mSocketCan1;
    DefaultDataLink mDataLink1;
    SocketCanProtocol mSocketCanProtocol1;
    byte[] mCanPack1 = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
    int mCanId1 = 0x102;//普通帧

    //两路can走一路port 透传
    SpiPort mSpi;
    DataLink mCPVxDataLink;
    Protocol mCPVxProtocol;

    byte[] mCanPack2 = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
    byte[] mCanPack3 = {(byte) 0x88, 0x77, 0x66, 0x55, 0x44, 0x33, 0x22, 0x11};

    Uart mRS232A;
    byte[] mRS232ATX = {'D', 'C', 'B', 'A'};
    Uart mRS232B;
    byte[] mRS232BTX = {'H', 'G', 'F', 'E'};

    int[] rxcount = new int[10];

    boolean isSpring = false;//spring1/2 使用 CPV1  , tank2/apollo2 使用 CPV3

    private String byte2String(byte[] array) {
        String txt = "";
        for (byte i : array) {
            txt += String.format(" x%02X", i);
        }
        return txt;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Send BTN
        Button btnSend = findViewById(R.id.send_btn);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w(TAG, "Sending...");
                sendMcuCan();
            }
        });

        //Initial
        runMcuCan();
    }

    private void runMcuCan()
    {
        try {
            //Two channels of CAN are transmitted through one SpiPort
            mSpi0 = new SpiPort(CPDEVICE.TANK2.SPI2_0, CPDEVICE.TANK2.SPI_DATA_IND, true);
            cpv1DataLink = new CPV1DataLink();
            cpv1Protocol = new CPV1Protocol();
            //Configure bus parameters
            cpv1Protocol.config(Protocol.CAN_BAUD_250K, Protocol.CAN_BAUD_250K);
            //Register the data listening callback
            cpv1Protocol.setCanFrameRxListener(new CPCanFrameRxListener() {
                @Override
                public void onReceive(int channel, int id, boolean idType, boolean remote, int dlc, byte[] canpack) {
                    Log.w(TAG, String.format("[0x%x, mcu%d, %b, %b, %d] %s\r\n", id, channel, idType, remote, dlc, byte2String(canpack)));
                    if (channel == ICPCanFrame.Channel.CHN_1.ordinal()) {
                        // recvice MCU CHANNEL1 CAN MESSAGE

                    } else if (channel == ICPCanFrame.Channel.CHN_2.ordinal()) {
                        // recvice MCU CHANNEL2 CAN MESSAGE
                    }
                }
            });
            cpv1Protocol.setDebug(true);
            //Establish virtual bus channel
            cpv1Protocol.connect(cpv1DataLink, mSpi0);
        } catch (CPBusException e) {//catch bus excpetion
            e.printStackTrace();
            Log.e(TAG, "runMcuCan: Error: "+e.getMessage());
        }
    }

    private void sendMcuCan() {
        byte[] mCanPack = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
        if (cpv1Protocol.isReady()) { //CPV1 need detect bus is ready!
            //send to mcu can channel 1
            cpv1Protocol.sendCanFrame(ICPCanFrame.Channel.CHN_1.ordinal(), 0x18109944, true, false, 8, mCanPack);
            //send to mcu can channel 2
            cpv1Protocol.sendCanFrame(ICPCanFrame.Channel.CHN_2.ordinal(), 0x18095588, true, false, 8, mCanPack);
        }else
        {
            Log.e(TAG, "cpv1Protocol not ready!");
        }
    }

    private void clearMcuCan()
    {
        //CPV3 release
        if (cpv1Protocol != null) {
            cpv1Protocol.disconnect();
            cpv1Protocol.release();
        }
        if (cpv1DataLink != null) {
            cpv1DataLink.release();
        }
        if (mSpi0 != null) {
            mSpi0.release();
        }
    }

    private void runSocketCan0()
    {
        try {
            mSocketCan0 = new SocketCan(CPDEVICE.TANK2.SOCKCAN0 , Protocol.CAN_BAUD_500K, Protocol.CAN_BAUD_2M, true);//端口

            mDataLink0 = new DefaultDataLink();//链路
            mSocketCanProtocol0 = new SocketCanProtocol();//协议
            //注册回调
            mSocketCanProtocol0.setCanFrameRxCallback(new SocketCanFrameRxListener() {
                @Override
                public void onCanReceive(int id, int dlc, byte[] canpack) {

                }

                @Override
                public void onCanFdReceive(int id, int flags, int datalen, byte[] canpack) {
                    rxcount[0]++;
                    Log.w(TAG, String.format("can0:0x%x, 0x%x, %d [%d], %s", id, flags, datalen, rxcount[0], byte2String(canpack)));
                    if (id == 0x101 && datalen == 12 && canpack[11] == 0x11 && canpack[0] == (byte) 0xCC) {
                        //向虚拟总线发送数据
                        mSocketCanProtocol0.sendCanfdFrame(0x102, SocketCanProtocol.CANFD_BRS, 12, mCanPack);
                        Log.w(TAG, "can0:recv client data, and reply a frame--->");
                    }
                }
            });
            //建立虚拟总线通道
            mSocketCanProtocol0.connect(mDataLink0, mSocketCan0);
        } catch (CPBusException e) {
            e.printStackTrace();
            Log.e(TAG, "mSocketCan0 Error: "+e.getMessage());
        }
    }

    private void runSocketCan1()
    {
        try {
            mSocketCan1 = new SocketCan(CPDEVICE.TANK2.SOCKCAN1, Protocol.CAN_BAUD_500K, Protocol.CAN_BAUD_2M, true);

            mDataLink1 = new DefaultDataLink();
            mSocketCanProtocol1 = new SocketCanProtocol();
            mSocketCanProtocol1.setCanFrameRxCallback(new SocketCanFrameRxListener() {
                @Override
                public void onCanReceive(int id, int dlc, byte[] canpack) {

                }

                @Override
                public void onCanFdReceive(int id, int flags, int datalen, byte[] canpack) {
                    rxcount[1]++;
                    Log.w(TAG, String.format("can1:0x%x, 0x%x, %d [%d]\n %s", id, flags, datalen, rxcount[1], byte2String(canpack)));
                    if (id == 0x103 && datalen == 12 && canpack[11] == 0x11 && canpack[0] == (byte) 0xCC) {
                        mSocketCanProtocol1.sendCanfdFrame(0x104, SocketCanProtocol.CANFD_BRS, 12, mCanPack);
                        Log.w(TAG, "can1:recv client data, and reply a frame--->");
                    }
                }
            });
            mSocketCanProtocol1.connect(mDataLink1, mSocketCan1);
        } catch (CPBusException e) {
            e.printStackTrace();
        }
    }

    private void runOther()
    {
        try {
            if (isSpring) {
                mSpi = new SpiPort(CPDEVICE.SPRING.SPI2_0, CPDEVICE.SPRING.SPI_DATA_IND, true);
                mCPVxDataLink = new CPV1DataLink();
                mCPVxProtocol = new CPV1Protocol();
                ((CPV1Protocol)mCPVxProtocol).config(Protocol.CAN_BAUD_500K,Protocol.CAN_BAUD_500K);
            } else {
                mSpi = new SpiPort(CPDEVICE.TANK2.SPI2_0, CPDEVICE.TANK2.SPI_DATA_IND, true);
                mCPVxDataLink = new CPV3DataLink();
                mCPVxProtocol = new CPV3Protocol();
                ((CPV3Protocol)mCPVxProtocol).config(Protocol.CAN_BAUD_500K, Protocol.CAN_BAUD_500K);
            }


            ((ICPCanFrame)mCPVxProtocol).setCanFrameRxListener(new CPCanFrameRxListener() {
                @Override
                public void onReceive(int channel, int id, boolean idType, boolean remote, int dlc, byte[] canpack) {
                    rxcount[2]++;
                    Log.w(TAG, String.format("0x%x, mcu%d, %b, %b, %d [%d]\r\n%s\r\n", id, channel, idType, remote, dlc, rxcount[2], byte2String(canpack)));
                    if (channel == ICPCanFrame.Channel.CHN_1.ordinal()) {
                        if (id == 0x105 && dlc == 8 && canpack[7] == 0x11 && canpack[0] == (byte) 0x88) {
                            if (mCPVxProtocol.isReady()) {//CPV3 need detect bus is ready!
                                ((ICPCanFrame)mCPVxProtocol).sendCanFrame(channel, 0x106, false, false, 8, mCanPack2);
                                Log.w(TAG, "mcu1:recv client data, and reply a frame--->");
                            }
                        }
                    } else if (channel == ICPCanFrame.Channel.CHN_2.ordinal()) {
                        if (id == 0x107 && dlc == 8 && canpack[7] == 0x11 && canpack[0] == (byte) 0x88) {
                            if (mCPVxProtocol.isReady()) {
                                ((ICPCanFrame)mCPVxProtocol).sendCanFrame(channel, 0x108, false, false, 8, mCanPack2);
                            }
                            Log.w(TAG, "mcu2:recv client data, and reply a frame--->");
                        }
                    }
                }
            });

            mCPVxProtocol.connect(mCPVxDataLink, mSpi);
        } catch (CPBusException e) {
            e.printStackTrace();
        }

    }

    private void runRs232()
    {
        try {
            mRS232A = new Uart(CPDEVICE.TANK2.RS232_A, Uart.B115200);

            mRS232A.setReceiveListener(new Port.DataReceiveListener() {
                @Override
                public void onReceive(byte[] bytes) {
                    if (bytes.length >= 4) {
                        rxcount[3]++;
                        Log.w(TAG, String.format("RS232A:recv %c%c%c%c\r\n", bytes[0], bytes[1], bytes[2], bytes[3]));
                        if (bytes[0] == 'A' && bytes[3] == 'D') {
                            mRS232A.send(mRS232ATX);
                            Log.w(TAG, "RS232A:reply DCBA [" + rxcount[3] + "]--->");
                        }
                    }
                }
            });
            mRS232A.start();
        } catch (CPBusException e) {
            e.printStackTrace();
        }
    }

    private void runUart()
    {
        try {
            Uart testCPBusExceptionUart = new Uart("/dev/ttyS11", Uart.B115200);
            testCPBusExceptionUart.setReceiveListener(new Port.DataReceiveListener() {
                @Override
                public void onReceive(byte[] bytes) {
                    Log.w(TAG, "Got data: "+bytes.length);
                }
            });
            testCPBusExceptionUart.start();
        } catch (CPBusException e) {
            e.printStackTrace();
            Log.e(TAG, "testCPBusExceptionUart: "+e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //MCU CAN release
        clearMcuCan();

        //socketcan 0 release
        if(mSocketCanProtocol0!=null)mSocketCanProtocol0.disconnect();
        if(mSocketCanProtocol0!=null)mSocketCanProtocol0.release();
        if(mDataLink0!=null)mDataLink0.release();
        if(mDataLink0!=null)mSocketCan0.release();

        //socketcan 0 release
        if(mSocketCanProtocol1!=null)mSocketCanProtocol1.disconnect();
        if(mSocketCanProtocol1!=null)mSocketCanProtocol1.release();
        if(mDataLink1!=null)mDataLink1.release();
        if(mSocketCan1!=null)mSocketCan1.release();

        //CPV3 release
        if(mCPVxProtocol!=null)mCPVxProtocol.disconnect();
        if(mCPVxProtocol!=null)mCPVxProtocol.release();
        if(mCPVxDataLink!=null)mCPVxDataLink.release();
        if(mSpi!=null)mSpi.release();

        //uart release
        if(mRS232A!=null)mRS232A.release();
        if(mRS232B!=null)mRS232B.release();
    }

}