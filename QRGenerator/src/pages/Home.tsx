import {
  IonPage,
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonButton,
  IonCard,
  IonCardHeader,
  IonCardTitle,
  IonCardContent,
  IonLabel,
  IonText,
  IonChip,
  IonGrid,
  IonRow,
  IonCol,
  IonIcon,
} from "@ionic/react";
import { qrCodeOutline, logOutOutline } from 'ionicons/icons';
import { useAuth } from "../contexts/auth";
import { useEffect, useState } from "react";
import { v4 } from "uuid";
import { firebase, database } from "../../config";
import CreateQrModal from "../components/CreateQRModal";
import "./Home.css";

type QR_CODE = {
  description: string;
  name: string;
  status: "active" | "inactive" | "used" | "generated" | "not_generated";
  timestamp: string;
  user_id?: string;
};

function listenToQrCodes(onUpdate: (data: Record<string, QR_CODE>) => void) {
  const ref = database.ref("qr_codes");
  const listener = ref.on("value", (snapshot) => {
    const data = snapshot.val() || {};
    onUpdate(data);
  });
  return () => ref.off("value", listener);
}

export default function Home() {
  const { user, logout } = useAuth();
  const [qrCodes, setQrCodes] = useState<Record<string, QR_CODE>>({});
  const [showModal, setShowModal] = useState(false);

  function createQrCode(data: { name: string; description: string }) {
    try {
      const id = v4();
      database.ref("qr_codes/" + id).set({
        ...data,
        status: "generated",
        timestamp: firebase.database.ServerValue.TIMESTAMP,
      } as QR_CODE);
    } catch (error) {
      console.error("Error creating QR code:", error);
      throw error;
    }
  }

  useEffect(() => {
    const unsubscribe = listenToQrCodes((data) => {
      setQrCodes(data);
    });

    return () => {
      unsubscribe();
    };
  }, []);

  return (
    <IonPage>
      <CreateQrModal
        isOpen={showModal}
        onDismiss={() => setShowModal(false)}
        onSubmit={(data) => {
          createQrCode(data);
          setShowModal(false);
        }}
      />

      <IonHeader>
        <IonToolbar>
          <IonTitle>Dashboard</IonTitle>
        </IonToolbar>
      </IonHeader>

      <IonContent className="ion-padding">
        <IonGrid>
          <IonRow className="home-header-row">
            <IonCol size="12">
              <h2>Welcome, {user?.email}</h2>
              <IonButton onClick={logout} color="danger" size="small">
                <IonIcon slot="start" icon={logOutOutline}></IonIcon>
                Log Out
              </IonButton>
              <IonButton onClick={() => setShowModal(true)} size="small" className="" >
                <IonIcon slot="start" icon={qrCodeOutline}></IonIcon>
                Create QR Code
              </IonButton>
            </IonCol>
          </IonRow>

          <IonRow className="qr-card-grid">
            {Object.entries(qrCodes).map(([id, info]) => (
              <IonCol key={id} className="qr-col">
                <IonCard className="qr-card">
                  <IonCardHeader>
                    <IonCardTitle>{info.name}</IonCardTitle>
                  </IonCardHeader>
                  <IonCardContent>
                    <IonText color="medium">
                      {info.description || <i>No description</i>}
                    </IonText>
                    <div className="qr-card-footer">
                      <IonChip className={`status-chip ${info.status}`}>
                        {info.status}
                      </IonChip>
                      <IonLabel className="qr-id">ID: {id}</IonLabel>
                    </div>
                  </IonCardContent>
                </IonCard>
              </IonCol>
            ))}
          </IonRow>
        </IonGrid>
      </IonContent>
    </IonPage>
  );
}
