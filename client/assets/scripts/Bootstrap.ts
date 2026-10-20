import {
  _decorator,
  Camera,
  Canvas,
  Component,
  Layers,
  Node,
  ResolutionPolicy,
  UITransform,
  view
} from 'cc';
import {GameTable} from './GameTable';

const {ccclass} = _decorator;

/** Scene entry point. Creates the entire 2D table without external textures or prefabs. */
@ccclass('Bootstrap')
export class Bootstrap extends Component {
  onLoad(): void {
    view.setDesignResolutionSize(1280, 720, ResolutionPolicy.SHOW_ALL);
    const root = new Node('Canvas');
    root.layer = Layers.Enum.UI_2D;
    root.setParent(this.node);
    root.addComponent(UITransform).setContentSize(1280, 720);
    root.setPosition(640, 360, 0);
    const cameraNode = new Node('UICamera');
    cameraNode.setParent(root);
    cameraNode.setPosition(0, 0, 1000);
    const camera = cameraNode.addComponent(Camera);
    camera.projection = Camera.ProjectionType.ORTHO;
    camera.orthoHeight = 360;
    camera.near = 0.1;
    camera.far = 2000;
    camera.visibility = Layers.Enum.UI_2D;
    const canvas = root.addComponent(Canvas);
    canvas.cameraComponent = camera;
    root.addComponent(GameTable);
  }
}
